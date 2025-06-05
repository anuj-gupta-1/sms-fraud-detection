import requests
import json
import re
from flask import Flask, request, jsonify
from flask_cors import CORS
import logging
from datetime import datetime
import csv # For CSV operations
import os # For checking file existence
import threading # For thread-safe CSV writing

# Set up logging to see what's happening
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Ollama configuration
RECOMMENDED_MODELS = [
    "llama3.2:3b", "gemma2:2b", "phi3:3.8b", "qwen2.5:3b", "mistral:7b", "llama3.1:8b"
]
OLLAMA_API_URL = "http://localhost:11434/api/generate"
OLLAMA_MODEL = "llama3.2:3b"

# --- Enhancement 1: Suspicious Number Watchlist ---
SUSPICIOUS_NUMBERS_FILE = 'suspicious_numbers.csv'
WATCHLIST_NUMBERS = {} # Cache for loaded suspicious numbers: {'+919876500001': {'name': 'ScammerRavi', ...}}
# --- End Enhancement 1 Data ---

# --- Enhancement 3: High-Confidence Scam Logging ---
HIGH_CONFIDENCE_SCAMS_FILE = 'high_confidence_scams.csv'
SCAM_LOG_FIELDNAMES = ['timestamp', 'sender_id', 'message_content', 'analysis_json']
scam_log_lock = threading.Lock() # To prevent race conditions when writing to CSV
# --- End Enhancement 3 Data ---


def load_suspicious_numbers():
    """Loads suspicious numbers from CSV into memory."""
    global WATCHLIST_NUMBERS
    WATCHLIST_NUMBERS = {} # Reset before loading
    try:
        if os.path.exists(SUSPICIOUS_NUMBERS_FILE):
            with open(SUSPICIOUS_NUMBERS_FILE, mode='r', newline='', encoding='utf-8') as csvfile:
                reader = csv.DictReader(csvfile)
                for row in reader:
                    # Normalize phone number: ensure country code is present, default to +91 if ambiguous
                    # This is a simple normalization, real-world scenarios are complex.
                    phone_number = row.get('phone_number', '').strip()
                    country_code = row.get('country_code', '').strip()

                    if not phone_number:
                        continue

                    if phone_number.startswith('+'):
                        full_number = phone_number
                    elif country_code:
                        full_number = f"+{country_code}{phone_number}"
                    elif len(phone_number) == 10 and phone_number.isdigit(): # Assume Indian number if 10 digits and no CC
                        full_number = f"+91{phone_number}"
                    else:
                        full_number = phone_number # Store as is if unsure

                    WATCHLIST_NUMBERS[full_number] = {
                        'name': row.get('name'),
                        'source': row.get('source'),
                        'detection_date': row.get('detection_date')
                    }
            logger.info(f"👁️ Loaded {len(WATCHLIST_NUMBERS)} numbers into watchlist from {SUSPICIOUS_NUMBERS_FILE}")
        else:
            logger.warning(f"⚠️ Watchlist file not found: {SUSPICIOUS_NUMBERS_FILE}. Watchlist will be empty.")
    except Exception as e:
        logger.error(f"💥 Error loading suspicious numbers: {e}")

def check_sender_watchlist(sender_number):
    """
    Checks if a sender number (expected with +countrycode) is on the watchlist.
    Returns "on_watchlist" or "none".
    """
    # Basic normalization for lookup (e.g. removing spaces, though watchlist should be clean)
    normalized_sender = sender_number.replace(" ", "").strip()
    if normalized_sender in WATCHLIST_NUMBERS:
        logger.info(f"🚦 Sender {normalized_sender} found on watchlist.")
        return "on_watchlist"
    
    # Check for partial matches for Indian numbers if +91 is missing and sender is 10 digits
    if not normalized_sender.startswith('+') and len(normalized_sender) == 10 and normalized_sender.isdigit():
        if f"+91{normalized_sender}" in WATCHLIST_NUMBERS:
            logger.info(f"🚦 Sender +91{normalized_sender} (derived) found on watchlist.")
            return "on_watchlist"
            
    return "none"


def log_high_confidence_scam(sender_id, message_content, analysis_result):
    """Appends high-confidence scam details to a CSV file."""
    try:
        with scam_log_lock: # Ensure thread-safe writes
            file_exists = os.path.isfile(HIGH_CONFIDENCE_SCAMS_FILE)
            with open(HIGH_CONFIDENCE_SCAMS_FILE, mode='a', newline='', encoding='utf-8') as csvfile:
                writer = csv.DictWriter(csvfile, fieldnames=SCAM_LOG_FIELDNAMES)
                if not file_exists or os.path.getsize(HIGH_CONFIDENCE_SCAMS_FILE) == 0:
                    writer.writeheader() # Write header only if file is new/empty

                # Prepare data for CSV
                # The 'analysis_result' is the core AI response part, not the full metadata-added one.
                # We'll store the 'analysis_result' part of the 'result' from /analyze before further metadata is added.
                log_entry = {
                    'timestamp': datetime.now().isoformat(),
                    'sender_id': sender_id,
                    'message_content': message_content,
                    'analysis_json': json.dumps(analysis_result) # Store the detailed AI analysis as a JSON string
                }
                writer.writerow(log_entry)
                logger.info(f"📝 Logged high-confidence scam from {sender_id} to {HIGH_CONFIDENCE_SCAMS_FILE}")
    except Exception as e:
        logger.error(f"💥 Error logging high-confidence scam: {e}")


def classify_sms_with_ollama(sms_text, sender_number): # Unchanged from your version
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
REASON: [one sentence explanation]""" #

    payload = {
        "model": OLLAMA_MODEL,
        "prompt": prompt,
        "stream": False,
        "options": {
            "temperature": 0.1,
            "top_p": 0.8,
            "num_predict": 100,
            "repeat_penalty": 1.1
        }
    } #

    try:
        logger.info(f"🤖 Analyzing with {OLLAMA_MODEL}: {sms_text[:50]}...") #
        
        response = requests.post(
            OLLAMA_API_URL, 
            json=payload, 
            headers={"Content-Type": "application/json"}, 
            timeout=20
        ) #
        response.raise_for_status() #

        response_data = response.json() #
        raw_response = response_data.get("response", "").strip() #
        
        logger.info(f"🔍 AI Response: {raw_response}") #

        classification = "LEGITIMATE"
        confidence_score = 60
        ai_reason = "Unable to parse AI response" #
        
        try:
            if "CLASSIFICATION:" in raw_response:
                class_match = re.search(r'CLASSIFICATION:\s*(SCAM|LEGITIMATE)', raw_response, re.IGNORECASE) #
                if class_match:
                    classification = class_match.group(1).upper() #
            
            if "CONFIDENCE:" in raw_response:
                conf_match = re.search(r'CONFIDENCE:\s*(\d+)', raw_response) #
                if conf_match:
                    confidence_score = min(100, max(50, int(conf_match.group(1)))) #
            
            if "REASON:" in raw_response:
                reason_match = re.search(r'REASON:\s*(.+)', raw_response, re.IGNORECASE) #
                if reason_match:
                    ai_reason = reason_match.group(1).strip() #
        
        except Exception as parse_error:
            logger.warning(f"⚠️ Parse error: {parse_error}") #
            scam_keywords = ["urgent", "suspended", "verify now", "click here", "act now", "winner", "prize", "ssn", "social security"] #
            if any(keyword in sms_text.lower() for keyword in scam_keywords): #
                classification = "SCAM" #
                confidence_score = 65 #
                ai_reason = "Keyword-based detection found scam indicators" #
            else:
                classification = "LEGITIMATE" #
                confidence_score = 70 #
                ai_reason = "No clear scam indicators found" #

        if confidence_score >= 85:
            confidence_level = "VERY_HIGH"
        elif confidence_score >= 75:
            confidence_level = "HIGH"
        elif confidence_score >= 65:
            confidence_level = "MEDIUM"
        else:
            confidence_level = "LOW" #

        if classification == "SCAM":
            risk_score = min(0.9, confidence_score / 100.0) #
        else:
            risk_score = max(0.05, (100 - confidence_score) / 200.0) #

        return {
            "classification": classification,
            "confidence": confidence_level,
            "confidence_score": confidence_score,
            "reason": ai_reason,
            "risk_score": round(risk_score, 3),
            "detection_method": "LLM",
            "model_used": OLLAMA_MODEL,
            "processing_time": "< 2s" # This is a static placeholder from original code
        } #

    except requests.exceptions.ConnectionError:
        logger.error("❌ Ollama connection failed - is Ollama running?") #
        return analyze_with_fallback_rules(sms_text, sender_number, "OLLAMA_OFFLINE")
    except requests.exceptions.Timeout:
        logger.error("⏱️ Ollama timeout") #
        return analyze_with_fallback_rules(sms_text, sender_number, "TIMEOUT")
    except Exception as e:
        logger.error(f"💥 Unexpected error: {e}") #
        return analyze_with_fallback_rules(sms_text, sender_number, "ERROR")

def analyze_with_fallback_rules(sms_text, sender_number, error_type): # Mostly unchanged
    """
    Rule-based fallback when AI is unavailable - more conservative approach
    """
    logger.info(f"🔄 Using fallback rules due to: {error_type}") #
    
    text_lower = sms_text.lower() #
    
    urgent_threats = ["urgent", "suspended", "closed", "blocked", "expired"] #
    action_demands = ["click here", "verify now", "act now", "immediate", "within 24"] #
    sensitive_requests = ["ssn", "social security", "password", "pin", "bank account", "routing"] #
    impersonation = ["irs", "government", "police", "fbi", "court"] #
    
    legitimate_senders = ["amazon", "fedex", "ups", "usps", "cvs", "walgreens", "google", "apple"] #
    legitimate_content = ["delivered", "shipped", "prescription ready", "appointment", "verification code", "otp"] #
    
    urgent_count = sum(1 for word in urgent_threats if word in text_lower) #
    action_count = sum(1 for phrase in action_demands if phrase in text_lower) #
    sensitive_count = sum(1 for word in sensitive_requests if word in text_lower) #
    imperson_count = sum(1 for word in impersonation if word in text_lower) #
    
    legit_sender = any(sender in sender_number.lower() for sender in legitimate_senders) #
    legit_content_count = sum(1 for phrase in legitimate_content if phrase in text_lower) #
    
    total_scam_indicators = urgent_count + action_count + sensitive_count + imperson_count #
    
    if sensitive_count >= 1 and (urgent_count >= 1 or action_count >= 1):
        classification = "SCAM" #
        confidence_score = 85 #
        reason = "Requests sensitive information with urgent language" #
        risk_score = 0.8 #
    elif total_scam_indicators >= 3:
        classification = "SCAM" #
        confidence_score = 75 #
        reason = f"Multiple scam indicators detected ({total_scam_indicators})" #
        risk_score = 0.7 #
    elif legit_sender or legit_content_count >= 1:
        classification = "LEGITIMATE" #
        confidence_score = 80 #
        reason = "Matches legitimate business communication patterns" #
        risk_score = 0.1 #
    else:
        classification = "LEGITIMATE" #
        confidence_score = 60 #
        reason = "No clear scam indicators found" #
        risk_score = 0.2 #
    
    if confidence_score >= 85:
        confidence_level = "VERY_HIGH"
    elif confidence_score >= 75:
        confidence_level = "HIGH"
    elif confidence_score >= 65:
        confidence_level = "MEDIUM"
    else:
        confidence_level = "LOW" #
    
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
    } #

def get_alert_level(classification, confidence_score): # Unchanged
    """Determines alert level for UI"""
    if classification == "SCAM":
        if confidence_score >= 80: #
            return "HIGH"
        elif confidence_score >= 65: #
            return "MEDIUM"
        else:
            return "LOW" #
    return "NONE" #

# Flask app setup
app = Flask(__name__)
CORS(app)

@app.route('/health', methods=['GET']) # Unchanged
def health_check():
    """Enhanced health check with model testing"""
    try:
        test_payload = {
            "model": OLLAMA_MODEL,
            "prompt": "Test: respond with OK",
            "stream": False,
            "options": {"num_predict": 10}
        } #
        
        response = requests.post(OLLAMA_API_URL, json=test_payload, timeout=5) #
        ollama_status = "CONNECTED" if response.status_code == 200 else "ERROR" #
        ollama_response_time = response.elapsed.total_seconds() #
        
    except:
        ollama_status = "OFFLINE" #
        ollama_response_time = 0 #
    
    return jsonify({
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "ollama_status": ollama_status,
        "current_model": OLLAMA_MODEL,
        "response_time_seconds": ollama_response_time,
        "recommended_models": RECOMMENDED_MODELS,
        "endpoints": ["/analyze", "/batch", "/test", "/models"],
        "detection_methods": ["LLM", "RULE_BASED"]
    }) #

@app.route('/models', methods=['GET']) # Unchanged
def list_models():
    """Get available Ollama models"""
    try:
        response = requests.get("http://localhost:11434/api/tags", timeout=5) #
        if response.status_code == 200:
            models = response.json().get("models", []) #
            return jsonify({
                "available_models": [model["name"] for model in models],
                "current_model": OLLAMA_MODEL,
                "recommended": RECOMMENDED_MODELS
            }) #
    except:
        pass #
    
    return jsonify({
        "error": "Could not fetch models - ensure Ollama is running",
        "current_model": OLLAMA_MODEL,
        "recommended": RECOMMENDED_MODELS
    }) #

@app.route('/analyze', methods=['POST'])
def analyze_sms():
    """Main SMS analysis endpoint - MODIFIED"""
    try:
        data = request.get_json() #
        if not data:
            return jsonify({"error": "JSON body required"}), 400 #
        
        message_content = data.get('message', '').strip() # Renamed for clarity
        sender_id = data.get('sender', 'Unknown') # Renamed for clarity
        
        if not message_content:
            return jsonify({"error": "Message field required"}), 400 #
        
        start_time = datetime.now() #
        # Core analysis result from LLM or fallback
        core_analysis_result = classify_sms_with_ollama(message_content, sender_id) #
        processing_time = (datetime.now() - start_time).total_seconds() #
        
        # Create the final result object to be sent to client
        final_result = core_analysis_result.copy()

        # Enhancement 1: Check sender watchlist
        final_result["sender_watchlist_status"] = check_sender_watchlist(sender_id)

        # Add other metadata (some might be redundant if already in core_analysis_result but this ensures they are set)
        final_result.update({
            "sender": sender_id, # Original sender from request
            "message_preview": message_content[:50] + "..." if len(message_content) > 50 else message_content, #
            "alert_level": get_alert_level(final_result["classification"], final_result["confidence_score"]), #
            "processing_time_seconds": round(processing_time, 2), #
            "timestamp": datetime.now().isoformat() #
        })
        
        # Enhancement 3: Log high-confidence scams
        # Using alert_level as per user's choice (Option B)
        if final_result.get("alert_level") == "HIGH":
            # Pass the original message_content and sender_id, and the core_analysis_result
            log_high_confidence_scam(sender_id, message_content, core_analysis_result) 
            
        logger.info(f"✅ Result for {sender_id}: {final_result['classification']} ({final_result['confidence']}), Watchlist: {final_result['sender_watchlist_status']}")
        return jsonify(final_result)
        
    except Exception as e:
        logger.error(f"💥 Route error: {e}") #
        return jsonify({
            "error": "Internal server error",
            "classification": "ERROR",
            "detection_method": "ERROR_HANDLER" #
        }), 500

@app.route('/batch', methods=['POST']) # Modified for new fields and logging
def batch_analyze():
    """Analyze multiple messages"""
    try:
        data = request.get_json() #
        messages_data = data.get('messages', []) # Renamed from 'messages' in original code
        
        if not messages_data or len(messages_data) == 0:
            return jsonify({"error": "Messages array required"}), 400 #
        
        response_results = []
        stats = {"scam": 0, "legitimate": 0, "llm_used": 0, "rules_used": 0, "on_watchlist": 0} # Added on_watchlist
        
        for msg_data in messages_data[:20]:  # Increased limit slightly, adjust as needed
            text = msg_data.get('message', '').strip() #
            sender = msg_data.get('sender', 'Unknown') #
            
            if text:
                # Simulate the full /analyze flow for each message to include watchlist and logging
                start_time = datetime.now()
                core_analysis_result = classify_sms_with_ollama(text, sender)
                processing_time = (datetime.now() - start_time).total_seconds()
                
                # Create the final result object for this message
                current_result = core_analysis_result.copy()
                current_result["sender_watchlist_status"] = check_sender_watchlist(sender)
                current_result.update({
                    "sender": sender,
                    "message_preview": text[:50] + "..." if len(text) > 50 else text,
                    "alert_level": get_alert_level(current_result["classification"], current_result["confidence_score"]),
                    "processing_time_seconds": round(processing_time, 2),
                    "timestamp": datetime.now().isoformat()
                })

                response_results.append(current_result) #
                
                # Update stats
                stats[current_result['classification'].lower()] = stats.get(current_result['classification'].lower(), 0) + 1 #
                if current_result['detection_method'] == 'LLM': #
                    stats['llm_used'] += 1 #
                else:
                    stats['rules_used'] += 1 #
                if current_result["sender_watchlist_status"] == "on_watchlist":
                    stats['on_watchlist'] += 1

                # Log if high confidence scam
                if current_result.get("alert_level") == "HIGH":
                    log_high_confidence_scam(sender, text, core_analysis_result)
        
        return jsonify({
            "results": response_results,
            "count": len(response_results),
            "statistics": stats
        }) #
        
    except Exception as e:
        return jsonify({"error": f"Batch processing failed: {e}"}), 500 #

@app.route('/test', methods=['GET']) # Modified to include watchlist status
def test_examples():
    """Test with diverse examples to check for over-classification"""
    test_cases = [
        {"message": "Your Amazon package will be delivered today by 8 PM", "sender": "Amazon", "expected": "LEGITIMATE"}, #
        {"message": "Prescription ready for pickup at CVS Pharmacy", "sender": "CVS", "expected": "LEGITIMATE"}, #
        {"message": "URGENT! Account suspended. Click bit.ly/verify123 to restore access NOW!", "sender": "+1234567890", "expected": "SCAM"}, #
        {"message": "Congratulations! You won $5000! Reply with your SSN to claim prize", "sender": "Unknown", "expected": "SCAM"}, #
        {"message": "IRS Notice: You owe $2000 in back taxes. Pay immediately or face arrest.", "sender": "+919876500001", "expected": "SCAM"}, # Example watchlist number
        {"message": "Hi, this is Sarah from the dentist office confirming your appointment", "sender": "+5551234567", "expected": "LEGITIMATE"}, #
    ]
    
    results_for_response = []
    correct_count = 0 #
    
    for test in test_cases:
        core_result = classify_sms_with_ollama(test["message"], test["sender"]) #
        is_correct = core_result["classification"] == test["expected"] #
        if is_correct:
            correct_count += 1 #
            
        watchlist_status = check_sender_watchlist(test["sender"])

        results_for_response.append({
            "message": test["message"], #
            "sender": test["sender"], #
            "expected": test["expected"], #
            "actual_classification": core_result["classification"], # Renamed from "actual"
            "confidence": core_result["confidence_score"], #
            "method": core_result["detection_method"], #
            "correct": is_correct, #
            "reason": core_result["reason"], #
            "sender_watchlist_status": watchlist_status
        })
    
    accuracy = (correct_count / len(test_cases)) * 100 if test_cases else 0 #
    
    return jsonify({
        "test_results": results_for_response,
        "accuracy_percentage": round(accuracy, 1),
        "total_tests": len(test_cases),
        "correct_predictions": correct_count,
        "model_used": OLLAMA_MODEL
    }) #

if __name__ == '__main__':
    load_suspicious_numbers() # Load watchlist at startup
    print("🚀 SMS Scam Detection Server v3.1 (with Watchlist & Scam Logging)") #
    print("=" * 50) #
    print(f"🤖 Current Model: {OLLAMA_MODEL}") #
    print(f"📡 Server: http://localhost:5000") #
    print(f"👁️ Watchlist: Loaded from '{SUSPICIOUS_NUMBERS_FILE}' ({len(WATCHLIST_NUMBERS)} entries)")
    print(f"📝 Scam Log: Will be written to '{HIGH_CONFIDENCE_SCAMS_FILE}'")
    print("=" * 50) #
    print("📋 RECOMMENDED MODELS (install with 'ollama pull <model>'):") #
    for i, model in enumerate(RECOMMENDED_MODELS, 1): #
        print(f"   {i}. {model}") #
    print("=" * 50) #
    print("🔧 API ENDPOINTS:") #
    print("   GET  /health   - Server & model status") #
    print("   GET  /models   - Available Ollama models")   #
    print("   GET  /test     - Test accuracy with examples") #
    print("   POST /analyze  - Analyze single SMS") #
    print("   POST /batch    - Analyze multiple SMS") #
    print("=" * 50) #
    print("⚠️  SETUP REQUIRED:") #
    print("   1. Install Ollama: https://ollama.ai") #
    print("   2. Start Ollama: ollama serve") #
    print("   3. Install model: ollama pull llama3.2:3b") #
    print(f"  4. Create '{SUSPICIOUS_NUMBERS_FILE}' (optional, for watchlist feature)")
    print("=" * 50) #
    
    app.run(host='0.0.0.0', port=5000, debug=True) #