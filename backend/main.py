import requests
import json
import re
from flask import Flask, request, jsonify
from flask_cors import CORS
import logging
from datetime import datetime

# Set up logging to see what's happening
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Ollama configuration - Try these models in order of preference
RECOMMENDED_MODELS = [
    "llama3.2:3b",      # Best balance of speed and accuracy
    "gemma2:2b",        # Very fast, good for real-time
    "phi3:3.8b",        # Microsoft's efficient model
    "qwen2.5:3b",       # Good multilingual support
    "mistral:7b",       # Slower but very accurate
    "llama3.1:8b"       # Most accurate but slowest
]

OLLAMA_API_URL = "http://localhost:11434/api/generate"
OLLAMA_MODEL = "llama3.2:3b"  # Default - change based on what you have installed

def classify_sms_with_ollama(sms_text, sender_number):
    """
    Analyzes SMS for scam indicators using Ollama AI with balanced prompt
    Returns classification with detection method info
    """
    
    # Improved balanced prompt - focuses on being conservative
    prompt = f"""You are a careful SMS security analyst. Your job is to identify CLEAR scams while avoiding false alarms.

IMPORTANT GUIDELINES:
- Most legitimate businesses send SMS notifications
- Only classify as SCAM if there are OBVIOUS red flags
- When in doubt, classify as LEGITIMATE
- Normal business communications are NOT scams

CLEAR SCAM INDICATORS:
1. Urgent threats about account closure/suspension
2. Requests for passwords, SSN, or banking details
3. Prize/lottery winnings requiring personal info
4. Suspicious shortened links (bit.ly, tinyurl) with threats
5. Government impersonation (IRS, police) demanding payment
6. Too-good-to-be-true money offers

LEGITIMATE MESSAGE TYPES:
- Delivery notifications (Amazon, FedEx, UPS)
- Appointment reminders (doctors, services)
- Verification codes (2FA, OTP)
- Prescription notifications
- Order confirmations
- Service notifications from known companies
- Marketing messages from real businesses

ANALYZE THIS MESSAGE:
From: {sender_number}
Message: "{sms_text}"

Rules for your response:
- Be conservative - only flag obvious scams
- Consider the sender (known businesses vs random numbers)
- Look for multiple red flags, not just one keyword

Respond EXACTLY in this format:
CLASSIFICATION: [SCAM or LEGITIMATE]
CONFIDENCE: [number 50-100]
REASON: [one sentence explanation]"""

    payload = {
        "model": OLLAMA_MODEL,
        "prompt": prompt,
        "stream": False,
        "options": {
            "temperature": 0.1,  # Lower temperature for more consistent results
            "top_p": 0.8,
            "num_predict": 100,
            "repeat_penalty": 1.1
        }
    }

    try:
        logger.info(f"🤖 Analyzing with {OLLAMA_MODEL}: {sms_text[:50]}...")
        
        response = requests.post(
            OLLAMA_API_URL, 
            json=payload, 
            headers={"Content-Type": "application/json"}, 
            timeout=20
        )
        response.raise_for_status()

        response_data = response.json()
        raw_response = response_data.get("response", "").strip()
        
        logger.info(f"🔍 AI Response: {raw_response}")

        # Parse the structured response
        classification = "LEGITIMATE"  # Default to safe
        confidence_score = 60
        ai_reason = "Unable to parse AI response"
        
        try:
            # Extract classification
            if "CLASSIFICATION:" in raw_response:
                class_match = re.search(r'CLASSIFICATION:\s*(SCAM|LEGITIMATE)', raw_response, re.IGNORECASE)
                if class_match:
                    classification = class_match.group(1).upper()
            
            # Extract confidence
            if "CONFIDENCE:" in raw_response:
                conf_match = re.search(r'CONFIDENCE:\s*(\d+)', raw_response)
                if conf_match:
                    confidence_score = min(100, max(50, int(conf_match.group(1))))
            
            # Extract reason
            if "REASON:" in raw_response:
                reason_match = re.search(r'REASON:\s*(.+)', raw_response, re.IGNORECASE)
                if reason_match:
                    ai_reason = reason_match.group(1).strip()
        
        except Exception as parse_error:
            logger.warning(f"⚠️ Parse error: {parse_error}")
            # Conservative fallback - check for obvious scam keywords
            scam_keywords = ["urgent", "suspended", "verify now", "click here", "act now", "winner", "prize", "ssn", "social security"]
            if any(keyword in sms_text.lower() for keyword in scam_keywords):
                classification = "SCAM"
                confidence_score = 65
                ai_reason = "Keyword-based detection found scam indicators"
            else:
                classification = "LEGITIMATE"
                confidence_score = 70
                ai_reason = "No clear scam indicators found"

        # Confidence level mapping
        if confidence_score >= 85:
            confidence_level = "VERY_HIGH"
        elif confidence_score >= 75:
            confidence_level = "HIGH"
        elif confidence_score >= 65:
            confidence_level = "MEDIUM"
        else:
            confidence_level = "LOW"

        # Risk score calculation
        if classification == "SCAM":
            risk_score = min(0.9, confidence_score / 100.0)
        else:
            # For legitimate messages, risk should be very low
            risk_score = max(0.05, (100 - confidence_score) / 200.0)

        return {
            "classification": classification,
            "confidence": confidence_level,
            "confidence_score": confidence_score,
            "reason": ai_reason,
            "risk_score": round(risk_score, 3),
            "detection_method": "LLM",
            "model_used": OLLAMA_MODEL,
            "processing_time": "< 2s"
        }

    except requests.exceptions.ConnectionError:
        logger.error("❌ Ollama connection failed - is Ollama running?")
        return analyze_with_fallback_rules(sms_text, sender_number, "OLLAMA_OFFLINE")
    except requests.exceptions.Timeout:
        logger.error("⏱️ Ollama timeout")
        return analyze_with_fallback_rules(sms_text, sender_number, "TIMEOUT")
    except Exception as e:
        logger.error(f"💥 Unexpected error: {e}")
        return analyze_with_fallback_rules(sms_text, sender_number, "ERROR")

def analyze_with_fallback_rules(sms_text, sender_number, error_type):
    """
    Rule-based fallback when AI is unavailable - more conservative approach
    """
    logger.info(f"🔄 Using fallback rules due to: {error_type}")
    
    text_lower = sms_text.lower()
    
    # High-confidence scam patterns (multiple indicators needed)
    urgent_threats = ["urgent", "suspended", "closed", "blocked", "expired"]
    action_demands = ["click here", "verify now", "act now", "immediate", "within 24"]
    sensitive_requests = ["ssn", "social security", "password", "pin", "bank account", "routing"]
    impersonation = ["irs", "government", "police", "fbi", "court"]
    
    # Legitimate patterns
    legitimate_senders = ["amazon", "fedex", "ups", "usps", "cvs", "walgreens", "google", "apple"]
    legitimate_content = ["delivered", "shipped", "prescription ready", "appointment", "verification code", "otp"]
    
    # Count indicators
    urgent_count = sum(1 for word in urgent_threats if word in text_lower)
    action_count = sum(1 for phrase in action_demands if phrase in text_lower)
    sensitive_count = sum(1 for word in sensitive_requests if word in text_lower)
    imperson_count = sum(1 for word in impersonation if word in text_lower)
    
    legit_sender = any(sender in sender_number.lower() for sender in legitimate_senders)
    legit_content_count = sum(1 for phrase in legitimate_content if phrase in text_lower)
    
    # Decision logic - requires multiple red flags for SCAM classification
    total_scam_indicators = urgent_count + action_count + sensitive_count + imperson_count
    
    if sensitive_count >= 1 and (urgent_count >= 1 or action_count >= 1):
        # Clear scam: asking for sensitive info with urgency
        classification = "SCAM"
        confidence_score = 85
        reason = "Requests sensitive information with urgent language"
        risk_score = 0.8
    elif total_scam_indicators >= 3:
        # Multiple red flags
        classification = "SCAM"
        confidence_score = 75
        reason = f"Multiple scam indicators detected ({total_scam_indicators})"
        risk_score = 0.7
    elif legit_sender or legit_content_count >= 1:
        # Looks legitimate
        classification = "LEGITIMATE"
        confidence_score = 80
        reason = "Matches legitimate business communication patterns"
        risk_score = 0.1
    else:
        # Default to legitimate when uncertain
        classification = "LEGITIMATE"
        confidence_score = 60
        reason = "No clear scam indicators found"
        risk_score = 0.2
    
    # Map confidence score to level
    if confidence_score >= 85:
        confidence_level = "VERY_HIGH"
    elif confidence_score >= 75:
        confidence_level = "HIGH"
    elif confidence_score >= 65:
        confidence_level = "MEDIUM"
    else:
        confidence_level = "LOW"
    
    return {
        "classification": classification,
        "confidence": confidence_level,
        "confidence_score": confidence_score,
        "reason": reason,
        "risk_score": risk_score,
        "detection_method": "RULE_BASED",
        "model_used": "Fallback Rules",
        "error": error_type,
        "fallback_used": True
    }

def get_alert_level(classification, confidence_score):
    """Determines alert level for UI"""
    if classification == "SCAM":
        if confidence_score >= 80:
            return "HIGH"
        elif confidence_score >= 65:
            return "MEDIUM"
        else:
            return "LOW"
    return "NONE"

# Flask app setup
app = Flask(__name__)
CORS(app)

@app.route('/health', methods=['GET'])
def health_check():
    """Enhanced health check with model testing"""
    try:
        # Test Ollama connection
        test_payload = {
            "model": OLLAMA_MODEL,
            "prompt": "Test: respond with OK",
            "stream": False,
            "options": {"num_predict": 10}
        }
        
        response = requests.post(OLLAMA_API_URL, json=test_payload, timeout=5)
        ollama_status = "CONNECTED" if response.status_code == 200 else "ERROR"
        ollama_response_time = response.elapsed.total_seconds()
        
    except:
        ollama_status = "OFFLINE"
        ollama_response_time = 0
    
    return jsonify({
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "ollama_status": ollama_status,
        "current_model": OLLAMA_MODEL,
        "response_time_seconds": ollama_response_time,
        "recommended_models": RECOMMENDED_MODELS,
        "endpoints": ["/analyze", "/batch", "/test", "/models"],
        "detection_methods": ["LLM", "RULE_BASED"]
    })

@app.route('/models', methods=['GET'])
def list_models():
    """Get available Ollama models"""
    try:
        response = requests.get("http://localhost:11434/api/tags", timeout=5)
        if response.status_code == 200:
            models = response.json().get("models", [])
            return jsonify({
                "available_models": [model["name"] for model in models],
                "current_model": OLLAMA_MODEL,
                "recommended": RECOMMENDED_MODELS
            })
    except:
        pass
    
    return jsonify({
        "error": "Could not fetch models - ensure Ollama is running",
        "current_model": OLLAMA_MODEL,
        "recommended": RECOMMENDED_MODELS
    })

@app.route('/analyze', methods=['POST'])
def analyze_sms():
    """Main SMS analysis endpoint"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "JSON body required"}), 400
        
        message = data.get('message', '').strip()
        sender = data.get('sender', 'Unknown')
        
        if not message:
            return jsonify({"error": "Message field required"}), 400
        
        start_time = datetime.now()
        result = classify_sms_with_ollama(message, sender)
        processing_time = (datetime.now() - start_time).total_seconds()
        
        # Add metadata
        result.update({
            "sender": sender,
            "message_preview": message[:50] + "..." if len(message) > 50 else message,
            "alert_level": get_alert_level(result["classification"], result["confidence_score"]),
            "processing_time_seconds": round(processing_time, 2),
            "timestamp": datetime.now().isoformat()
        })
        
        logger.info(f"✅ Result: {result['classification']} ({result['confidence']}) via {result['detection_method']}")
        return jsonify(result)
        
    except Exception as e:
        logger.error(f"💥 Route error: {e}")
        return jsonify({
            "error": "Internal server error",
            "classification": "ERROR",
            "detection_method": "ERROR_HANDLER"
        }), 500

@app.route('/batch', methods=['POST'])
def batch_analyze():
    """Analyze multiple messages"""
    try:
        data = request.get_json()
        messages = data.get('messages', [])
        
        if not messages or len(messages) == 0:
            return jsonify({"error": "Messages array required"}), 400
        
        results = []
        stats = {"scam": 0, "legitimate": 0, "llm_used": 0, "rules_used": 0}
        
        for msg in messages[:5]:  # Limit to 5 for performance
            text = msg.get('message', '').strip()
            sender = msg.get('sender', 'Unknown')
            
            if text:
                result = classify_sms_with_ollama(text, sender)
                result['sender'] = sender
                results.append(result)
                
                # Update stats
                stats[result['classification'].lower()] += 1
                if result['detection_method'] == 'LLM':
                    stats['llm_used'] += 1
                else:
                    stats['rules_used'] += 1
        
        return jsonify({
            "results": results,
            "count": len(results),
            "statistics": stats
        })
        
    except Exception as e:
        return jsonify({"error": f"Batch processing failed: {e}"}), 500

@app.route('/test', methods=['GET'])
def test_examples():
    """Test with diverse examples to check for over-classification"""
    test_cases = [
        # Should be LEGITIMATE
        {"message": "Your Amazon package will be delivered today by 8 PM", "sender": "Amazon", "expected": "LEGITIMATE"},
        {"message": "Prescription ready for pickup at CVS Pharmacy", "sender": "CVS", "expected": "LEGITIMATE"},
        {"message": "Your verification code is 123456. Do not share this code.", "sender": "Google", "expected": "LEGITIMATE"},
        {"message": "Appointment reminder: Dr. Smith tomorrow at 2 PM", "sender": "HealthClinic", "expected": "LEGITIMATE"},
        {"message": "Your Uber ride is arriving in 3 minutes", "sender": "Uber", "expected": "LEGITIMATE"},
        {"message": "Order #12345 has been shipped. Track your package.", "sender": "FedEx", "expected": "LEGITIMATE"},
        
        # Should be SCAM
        {"message": "URGENT! Account suspended. Click bit.ly/verify123 to restore access NOW!", "sender": "+1234567890", "expected": "SCAM"},
        {"message": "Congratulations! You won $5000! Reply with your SSN to claim prize", "sender": "Unknown", "expected": "SCAM"},
        {"message": "IRS Notice: You owe $2000 in back taxes. Pay immediately or face arrest.", "sender": "+9876543210", "expected": "SCAM"},
        
        # Edge cases
        {"message": "Hi, this is Sarah from the dentist office confirming your appointment", "sender": "+5551234567", "expected": "LEGITIMATE"},
        {"message": "Flash sale! 50% off everything today only at our store!", "sender": "RetailStore", "expected": "LEGITIMATE"}
    ]
    
    results = []
    correct_count = 0
    
    for test in test_cases:
        result = classify_sms_with_ollama(test["message"], test["sender"])
        is_correct = result["classification"] == test["expected"]
        if is_correct:
            correct_count += 1
            
        results.append({
            "message": test["message"],
            "sender": test["sender"],
            "expected": test["expected"],
            "actual": result["classification"],
            "confidence": result["confidence_score"],
            "method": result["detection_method"],
            "correct": is_correct,
            "reason": result["reason"]
        })
    
    accuracy = (correct_count / len(test_cases)) * 100
    
    return jsonify({
        "test_results": results,
        "accuracy_percentage": round(accuracy, 1),
        "total_tests": len(test_cases),
        "correct_predictions": correct_count,
        "model_used": OLLAMA_MODEL
    })

if __name__ == '__main__':
    print("🚀 SMS Scam Detection Server v3.0")
    print("=" * 50)
    print(f"🤖 Current Model: {OLLAMA_MODEL}")
    print(f"📡 Server: http://localhost:5000")
    print("=" * 50)
    print("📋 RECOMMENDED MODELS (install with 'ollama pull <model>'):")
    for i, model in enumerate(RECOMMENDED_MODELS, 1):
        print(f"   {i}. {model}")
    print("=" * 50)
    print("🔧 API ENDPOINTS:")
    print("   GET  /health   - Server & model status")
    print("   GET  /models   - Available Ollama models")  
    print("   GET  /test     - Test accuracy with examples")
    print("   POST /analyze  - Analyze single SMS")
    print("   POST /batch    - Analyze multiple SMS")
    print("=" * 50)
    print("⚠️  SETUP REQUIRED:")
    print("   1. Install Ollama: https://ollama.ai")
    print("   2. Start Ollama: ollama serve")
    print("   3. Install model: ollama pull llama3.2:3b")
    print("=" * 50)
    
    app.run(host='0.0.0.0', port=5000, debug=True)