# SMS Fraud Detection System

A comprehensive, user-friendly solution for detecting fraudulent SMS messages on Android, powered by local Large Language Models (LLM) through Ollama.

## 🚀 Project Overview

This system empowers users to identify potentially fraudulent SMS messages by analyzing message content with advanced AI models running locally on a backend server. The project has been recently updated with a major focus on user experience, featuring a redesigned UI and a more intuitive workflow.

-   **Android App**: A modern, responsive interface for users to manage and analyze SMS messages.
-   **Python Backend**: An AI-powered analysis engine using LLM models via Ollama.

## 📱 Features

### Android Application
- **Redesigned UI**: A clean, modern interface using the "Guardian Blue" color theme for a professional and trustworthy feel.
- **Flippable SMS Cards**: An intuitive card-based layout where each SMS can be "flipped" to view its detailed analysis.
- **Dynamic Permission Handling**: A seamless permission flow where the UI automatically updates upon granting access, with non-intrusive prompts for optional permissions.
- **Batch Analysis**: Select up to 5 SMS messages for simultaneous analysis.
- **At-a-Glance Status**: A redesigned header and status card provide immediate feedback on server connection and app status.
- **Real-time Communication**: Secure, real-time communication with the backend for fast analysis.

### Backend System
- **AI-Powered Fraud Detection**: Utilizes local LLM models via Ollama for privacy-focused analysis.
- **Batch Processing API**: A robust `/batch` endpoint to handle multiple messages in a single request.
- **Suspicious Number Watchlist**: Instantly flag messages from known suspicious numbers.
- **Detailed Analysis Results**: Returns a comprehensive analysis, including classification, confidence scores, risk levels, and reasoning.

## 🎨 UI/UX Enhancements

The application has been completely redesigned with the user in mind.

-   **Guardian Blue Theme**: A professional color palette based on cool blues and grays to inspire trust and calmness.
-   **Card-Based Layout**: All SMS messages from the last 24 hours are displayed on individual cards.
-   **Interactive Analysis**: If an analysis is available, a "flip" icon appears. Tapping the card flips it over to reveal the results, with color-coding for immediate risk assessment (Red for Scam, Orange for Suspicious, Green for Legitimate).
-   **Streamlined Actions**: A single "Analyze Selected" button and a clear header simplify the user workflow.

## 🛠️ Technology Stack

### Android App (`android-app/`)
- **Language**: Java/Kotlin
- **IDE**: Android Studio
- **Framework**: Android SDK
- **Features**: SMS reading, HTTP requests, UI components

### Backend (`backend/`)
- **Language**: Python
- **IDE**: Visual Studio
- **AI Framework**: Ollama with LLM models
- **API**: RESTful web service
- **Dependencies**: flask flask_cors CORS requests json logging

## 📋 Prerequisites

Before running this project, make sure you have:

### For Android App
- Android Studio installed
- Android device or emulator
- Minimum Android API level: 23

### For Backend
- Python 3.x installed
- Ollama installed and configured
- LLM models downloaded and ready
- Required Python packages (see requirements.txt)

## 🚀 Getting Started

### Backend Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/anuj-gupta-1/sms-fraud-detection.git
   cd sms-fraud-detection/backend
   ```

2. **Install dependencies**
   ```bash
   pip install -r requirements.txt
   ```

3. **Start Ollama server**
   ```bash
   ollama serve
   ```

4. **Run the backend**
   ```bash
   python app.py
   ```
   The backend will start running on `http://localhost:5000` (or your configured port)

### Android App Setup

1. **Open Android Studio**
2. **Import Project**
   - Navigate to `sms-fraud-detection/android-app/`
   - Open the project in Android Studio
3. **Configure API endpoint**
   - Update the backend URL in your app configuration
   - Point to your running backend server
4. **Build and Run**
   - Connect your Android device or start an emulator
   - Click "Run" to install and launch the app

## 🔧 Configuration

### Backend Configuration
- Update API endpoints and ports in configuration files
- Configure Ollama model settings
- Set up any required environment variables
- Configure suspicious_numbers.csv for watchlist functionality

### Android Configuration
- Update network security configuration for HTTP requests
- Configure SMS permissions in AndroidManifest.xml
- Set backend server URL in app settings

### Watchlist Configuration
1. Create `suspicious_numbers.csv` in the backend directory with the following columns:
   - phone_number: The phone number (with or without country code)
   - country_code: Optional country code (e.g., 91 for India)
   - name: Optional identifier or name for the suspicious entity
   - source: Optional source of the watchlist entry
   - detection_date: Optional date when the number was identified

Example suspicious_numbers.csv:
```csv
phone_number,country_code,name,source,detection_date
+919876543210,91,ScammerA,User Report,2024-03-15
8765432109,91,ScammerB,Police Report,2024-03-16
+14443332222,1,ScammerC,FBI List,2024-03-17
```

### Enhanced Security Features
- **Watchlist-Based Detection**: Messages from known suspicious numbers are automatically classified as high-confidence threats
- **Confidence Override**: Watchlisted numbers trigger automatic high-confidence scam classification regardless of message content
- **Detailed Logging**: High-confidence scams are logged for further analysis

## 📱 How to Use

1. **Start the Backend**: Make sure your Python backend and Ollama server are running
2. **Launch Android App**: Open the app on your mobile device
3. **Analyze SMS**: Use the app to scan and analyze suspicious SMS messages
4. **Get Results**: View fraud detection results and confidence scores

## 🤝 Contributing

We welcome contributions to improve this SMS fraud detection system! Here's how you can help:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 Project Structure

```
sms-fraud-detection/
├── android-app/          # Android mobile application
│   ├── app/             # Main app module
│   ├── gradle/          # Gradle configuration
│   └── ...              # Other Android project files
├── backend/             # Python backend service
│   ├── app.py           # Main application file
│   ├── models/          # AI model configurations
│   └── ...              # Other backend files
├── README.md            # Project documentation
└── LICENSE              # License information
```

## 🐛 Known Issues & Troubleshooting

### Common Issues
- **Backend not connecting**: Ensure Ollama server is running and models are loaded
- **Android permissions**: Make sure SMS reading permissions are granted
- **Network issues**: Check firewall settings and network connectivity

### Solutions
- Restart Ollama server if models fail to load
- Check Android device permissions in Settings
- Verify backend API endpoint configuration in Android app

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**Anuj Gupta**
- GitHub: [@anuj-gupta-1](https://github.com/anuj-gupta-1)
- Email: ganuj.iit@gmail.com

## 🙏 Acknowledgments

- Ollama team for providing local LLM capabilities
- Open source community for various tools and libraries
- Contributors who help improve this project

## 📊 Future Enhancements

- [ ] Add more sophisticated ML models
- [ ] Implement user feedback learning
- [ ] Create web dashboard for analytics
- [ ] Add multi-language support
- [ ] Implement real-time SMS monitoring
- [ ] Add automated watchlist updates from trusted sources
- [ ] Implement watchlist sharing between instances

---

**⚠️ Disclaimer**: This tool is designed to assist in identifying potentially fraudulent messages. Always verify suspicious communications through official channels and exercise caution when dealing with unknown contacts.