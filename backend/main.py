import requests
import json
from flask import Flask, request, jsonify
from flask_cors import CORS
import logging

# Set up logging to see what's happening
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Ollama configuration
OLLAMA_API_URL = "http://localhost:11434/api/generate"
OLLAMA_MODEL = "phi:2.7b"  # You can change this to llama3:8b if you have it

def classify_sms_with_ollama(sms_text, sender_number):
    """
    Analyzes SMS for scam indicators using Ollama AI
    Returns classification and confidence score
    """
    # Enhanced prompt with more context
    prompt = f"""
    You are an expert SMS security analyst. Analyze this text message for scam indicators.
    
    Sender: {sender_number}
    Message: "{sms_text}"
    
    Look for these scam indicators:
    - Urgent language ("act now", "limited time")
    - Requests for personal information
    - Suspicious links
    - Prize/lottery claims
    - Financial threats
    - Impersonation of banks/government
    - Poor grammar/spelling
    - Unknown sender with suspicious content
    
    Respond with ONLY one word: "SCAM" or "LEGITIMATE"
    """

    payload = {
        "model": OLLAMA_MODEL,
        "prompt": prompt,
        "stream": False,
        "options": {
            "temperature": 0.1,  # Very low for consistent results
            "top_p": 0.9,
            "num_predict": 10    # Limit response to just a few words
        }
    }

    try:
        logger.info(f"Analyzing message from {sender_number}: {sms_text[:50]}...")
        
        response = requests.post(
            OLLAMA_API_URL, 
            json=payload, 
            headers={"Content-Type": "application/json"}, 
            timeout=30
        )
        response.raise_for_status()

        response_data = response.json()
        raw_response = response_data.get("response", "").strip().upper()
        
        logger.info(f"Raw AI response: {raw_response}")

        # Clean and parse response
        if "SCAM" in raw_response:
            return {
                "classification": "SCAM", 
                "confidence": "HIGH",
                "reason": "AI detected scam indicators in message content",
                "risk_score": 0.8
            }
        elif "LEGITIMATE" in raw_response or "NOT" in raw_response:
            return {
                "classification": "LEGITIMATE", 
                "confidence": "HIGH",
                "reason": "AI found no significant scam indicators",
                "risk_score": 0.1
            }
        else:
            # Fallback: basic keyword detection
            scam_keywords = [
                "winner", "congratulations", "prize", "lottery", "urgent", "act now",
                "click here", "verify account", "suspended", "limited time", 
                "bank account", "social security", "tax refund", "irs",
                "bitcoin", "investment", "make money", "work from home"
            ]
            
            text_lower = sms_text.lower()
            scam_indicators = sum(1 for keyword in scam_keywords if keyword in text_lower)
            
            if scam_indicators >= 2:
                return {
                    "classification": "SCAM", 
                    "confidence": "MEDIUM",
                    "reason": f"Found {scam_indicators} scam keywords using fallback detection",
                    "risk_score": 0.6
                }
            else:
                return {
                    "classification": "LEGITIMATE", 
                    "confidence": "LOW",
                    "reason": "No clear scam indicators found",
                    "risk_score": 0.3
                }

    except requests.exceptions.RequestException as e:
        logger.error(f"Ollama connection error: {e}")
        return {
            "classification": "ERROR", 
            "confidence": "NONE", 
            "error": "AI_UNAVAILABLE",
            "reason": "Cannot connect to Ollama AI service",
            "risk_score": 0.0
        }
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
        return {
            "classification": "ERROR", 
            "confidence": "NONE", 
            "error": "PROCESSING_ERROR",
            "reason": f"Analysis failed: {str(e)}",
            "risk_score": 0.0
        }

# Create Flask app
app = Flask(__name__)
CORS(app)  # Allow requests from Android app

@app.route('/health', methods=['GET'])
def health_check():
    """Simple endpoint to check if server is running"""
    return jsonify({
        "status": "healthy",
        "model": OLLAMA_MODEL,
        "timestamp": json.dumps({"timestamp": "now"})
    })

@app.route('/analyze', methods=['POST'])  # Changed from /classify_sms to match Android app
def analyze_sms_route():
    """
    Main endpoint for SMS classification - matches Android app expectations
    Expects: {"message": "sms text", "sender": "phone number"}
    Returns: {"classification": "SCAM/LEGITIMATE", "confidence": "HIGH/MEDIUM/LOW", "reason": "...", "risk_score": 0.0-1.0}
    """
    try:
        if not request.is_json:
            return jsonify({"error": "Request must be JSON"}), 400

        data = request.get_json()
        sms_text = data.get('message', '').strip()
        sender = data.get('sender', 'Unknown')

        if not sms_text:
            return jsonify({"error": "Missing 'message' field"}), 400

        logger.info(f"Processing SMS from {sender}")
        
        result = classify_sms_with_ollama(sms_text, sender)
        
        # Add metadata expected by Android app
        result.update({
            "sender": sender,
            "message_length": len(sms_text),
            "processed": True
        })
        
        logger.info(f"Classification: {result['classification']}")
        return jsonify(result)

    except Exception as e:
        logger.error(f"Route error: {e}")
        return jsonify({
            "error": "Internal server error",
            "classification": "ERROR",
            "confidence": "NONE",
            "reason": f"Server error: {str(e)}",
            "risk_score": 0.0
        }), 500

# Keep the original endpoint for backward compatibility
@app.route('/classify_sms', methods=['POST'])
def classify_sms_route():
    """
    Original endpoint - redirects to new analyze endpoint
    """
    return analyze_sms_route()

@app.route('/batch_classify', methods=['POST'])
def batch_classify_route():
    """
    Classify multiple SMS messages at once
    Expects: {"messages": [{"message": "text", "sender": "number"}, ...]}
    """
    try:
        if not request.is_json:
            return jsonify({"error": "Request must be JSON"}), 400

        data = request.get_json()
        messages = data.get('messages', [])

        if not messages or not isinstance(messages, list):
            return jsonify({"error": "Missing or invalid 'messages' array"}), 400

        results = []
        for msg_data in messages[:5]:  # Limit to 5 messages per batch
            sms_text = msg_data.get('message', '').strip()
            sender = msg_data.get('sender', 'Unknown')
            
            if sms_text:
                result = classify_sms_with_ollama(sms_text, sender)
                result['sender'] = sender
                results.append(result)

        return jsonify({
            "results": results,
            "total_processed": len(results)
        })

    except Exception as e:
        logger.error(f"Batch route error: {e}")
        return jsonify({"error": "Batch processing failed"}), 500

if __name__ == '__main__':
    print("="*50)
    print("🚀 SMS Scam Detection Server Starting...")
    print("="*50)
    print(f"📡 Server will run on: http://localhost:5000")  # Changed from 5001
    print(f"🤖 Using AI Model: {OLLAMA_MODEL}")
    print(f"🔗 Ollama API: {OLLAMA_API_URL}")
    print("="*50)
    print("📱 Endpoints available:")
    print("   GET  /health - Check server status")
    print("   POST /analyze - Analyze single SMS (Android app endpoint)")
    print("   POST /classify_sms - Analyze single SMS (legacy)")
    print("   POST /batch_classify - Analyze multiple SMS")
    print("="*50)
    print("⚠️  Make sure Ollama is running before using!")
    print("   Run: ollama serve")
    print("="*50)
    
    app.run(host='0.0.0.0', port=5000, debug=True)  # Changed from port 5001