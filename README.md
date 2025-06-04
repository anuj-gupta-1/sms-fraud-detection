# SMS Fraud Detection System

A comprehensive solution for detecting fraudulent SMS messages using machine learning. This project combines an Android mobile application with a Python backend powered by Large Language Models (LLM) through Ollama.

## 🚀 Project Overview

This system helps users identify potentially fraudulent SMS messages by analyzing message content using advanced AI models. The solution consists of two main components:

- **Android App**: Mobile interface for users to check SMS messages
- **Python Backend**: AI-powered analysis engine using LLM models via Ollama

## 📱 Features

### Android Application
- Read and analyze SMS messages from device
- User-friendly interface for fraud detection
- Real-time communication with backend API
- Secure message processing

### Backend System
- AI-powered fraud detection using LLM models
- RESTful API for mobile app integration
- Ollama integration for local model execution
- Fast and accurate fraud classification

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

### Android Configuration
- Update network security configuration for HTTP requests
- Configure SMS permissions in AndroidManifest.xml
- Set backend server URL in app settings

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

---

**⚠️ Disclaimer**: This tool is designed to assist in identifying potentially fraudulent messages. Always verify suspicious communications through official channels and exercise caution when dealing with unknown contacts.