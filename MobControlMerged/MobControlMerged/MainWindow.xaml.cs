using Newtonsoft.Json;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Drawing.Imaging;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using WindowsInput;
using WindowsInput.Native;
using ZXing;
using ZXing.Windows.Compatibility;

namespace MobControlDesktop
{
    public partial class MainWindow : Window
    {
        // Win32 API for mouse control
        [DllImport("user32.dll")]
        static extern bool SetCursorPos(int X, int Y);

        [DllImport("user32.dll")]
        static extern bool GetCursorPos(out POINT lpPoint);

        [StructLayout(LayoutKind.Sequential)]
        public struct POINT
        {
            public int X;
            public int Y;
        }

        private UdpClient udpServer;
        private Thread receiveThread;
        private string localIP;
        private string pairingCode;
        private int serverPort = 7777;
        private bool isServerRunning = false;
        private InputSimulator inputSimulator;

        // Button configuration
        private Dictionary<string, ButtonMapping> buttonMappings;

        // Device-specific mappings (IP Address -> Mappings)
        private Dictionary<string, Dictionary<string, ButtonMapping>> deviceMappings;

        // Connected devices
        private ObservableCollection<ConnectedDevice> connectedDevices;
        private Dictionary<string, IPEndPoint> deviceEndpoints;

        // Track joystick pressed state per device
        private Dictionary<string, JoystickState> deviceJoystickStates;

        // Track fractional mouse movement per device (right joystick)
        private Dictionary<string, MouseState> deviceMouseStates;

        // Dark mode state
        private bool isDarkMode = true;

        public MainWindow()
        {
            InitializeComponent();
            inputSimulator = new InputSimulator();
            connectedDevices = new ObservableCollection<ConnectedDevice>();
            deviceEndpoints = new Dictionary<string, IPEndPoint>();
            deviceMappings = new Dictionary<string, Dictionary<string, ButtonMapping>>();
            deviceJoystickStates = new Dictionary<string, JoystickState>();
            deviceMouseStates = new Dictionary<string, MouseState>();
            ConnectedDevicesList.ItemsSource = connectedDevices;
            LoadButtonMappings();
            ApplyTheme();
        }

        private void LoadButtonMappings()
        {
            buttonMappings = new Dictionary<string, ButtonMapping>
            {
                { "up", new ButtonMapping { Enabled = true, Key = "W", VirtualKey = VirtualKeyCode.VK_W } },
                { "down", new ButtonMapping { Enabled = true, Key = "S", VirtualKey = VirtualKeyCode.VK_S } },
                { "left", new ButtonMapping { Enabled = true, Key = "A", VirtualKey = VirtualKeyCode.VK_A } },
                { "right", new ButtonMapping { Enabled = true, Key = "D", VirtualKey = VirtualKeyCode.VK_D } },
                { "a", new ButtonMapping { Enabled = true, Key = "SPACE", VirtualKey = VirtualKeyCode.SPACE } },
                { "b", new ButtonMapping { Enabled = true, Key = "E", VirtualKey = VirtualKeyCode.VK_E } },
                { "x", new ButtonMapping { Enabled = true, Key = "Q", VirtualKey = VirtualKeyCode.VK_Q } },
                { "y", new ButtonMapping { Enabled = true, Key = "R", VirtualKey = VirtualKeyCode.VK_R } },
                { "go", new ButtonMapping { Enabled = true, Key = "UP", VirtualKey = VirtualKeyCode.UP } },
                { "back", new ButtonMapping { Enabled = true, Key = "DOWN", VirtualKey = VirtualKeyCode.DOWN } }
            };

            // Load from file if exists
            try
            {
                string filePath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "button_mappings.json");
                if (File.Exists(filePath))
                {
                    string json = File.ReadAllText(filePath);
                    var loaded = JsonConvert.DeserializeObject<Dictionary<string, ButtonMapping>>(json);
                    if (loaded != null)
                    {
                        buttonMappings = loaded;
                    }
                }
            }
            catch (Exception ex)
            {
                AddLog($"Failed to load mappings: {ex.Message}");
            }

            // Load device-specific mappings
            LoadDeviceMappings();
        }

        #region Navigation Methods

        private void DarkModeToggle_Click(object sender, RoutedEventArgs e)
        {
            isDarkMode = !isDarkMode;
            ApplyTheme();
        }

        private void ApplyTheme()
        {
            if (isDarkMode)
            {
                // Dark Mode
                this.Background = new SolidColorBrush(Color.FromRgb(30, 30, 30)); // #1E1E1E
                DarkModeToggle.Content = "🌙 Dark Mode";

                // Main Menu
                if (MainMenuScreen != null && MainMenuScreen.Children.Count > 1)
                {
                    var title = MainMenuScreen.Children[1] as TextBlock;
                    if (title != null)
                    {
                        title.Foreground = new SolidColorBrush(Colors.White);
                    }
                }

                // Host Screen - Dark Mode
                if (BackButton != null)
                {
                    BackButton.Background = new SolidColorBrush(Color.FromRgb(66, 66, 66));
                    BackButton.Foreground = new SolidColorBrush(Colors.White);
                }

                if (HostTitle != null)
                {
                    HostTitle.Foreground = new SolidColorBrush(Colors.White);
                }

                if (PairingCodeLabel != null)
                {
                    PairingCodeLabel.Foreground = new SolidColorBrush(Color.FromRgb(170, 170, 170)); // #AAAAAA
                }

                if (IPAddressText != null)
                {
                    IPAddressText.Foreground = new SolidColorBrush(Color.FromRgb(144, 202, 249)); // #90CAF9
                }

                if (StatusBorder != null)
                {
                    StatusBorder.Background = new SolidColorBrush(Color.FromRgb(44, 44, 44));
                    StatusBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(68, 68, 68));
                }

                if (StatusText != null)
                {
                    StatusText.Foreground = new SolidColorBrush(Colors.White);
                }

                if (LogBorder != null)
                {
                    LogBorder.Background = new SolidColorBrush(Color.FromRgb(44, 44, 44));
                    LogBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(68, 68, 68));
                }

                if (LogTitle != null)
                {
                    LogTitle.Foreground = new SolidColorBrush(Colors.White);
                }

                if (ConnectedDevicesBorder != null)
                {
                    ConnectedDevicesBorder.Background = new SolidColorBrush(Color.FromRgb(37, 37, 37)); // #252525
                    ConnectedDevicesBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(68, 68, 68));
                }

                if (ConnectedDevicesTitle != null)
                {
                    ConnectedDevicesTitle.Foreground = new SolidColorBrush(Colors.White);
                }

                if (ConnectionCountText != null)
                {
                    ConnectionCountText.Foreground = new SolidColorBrush(Color.FromRgb(170, 170, 170));
                }

                // Settings screen dark mode
                if (SettingsBorder != null)
                {
                    SettingsBorder.Background = new SolidColorBrush(Color.FromRgb(44, 44, 44));
                    SettingsBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(68, 68, 68));
                }

                if (SettingsTitle != null)
                {
                    SettingsTitle.Foreground = new SolidColorBrush(Colors.White);
                }

                if (NotifyOnConnect != null)
                {
                    NotifyOnConnect.Foreground = new SolidColorBrush(Color.FromRgb(204, 204, 204));
                }

                if (NotifyOnDisconnect != null)
                {
                    NotifyOnDisconnect.Foreground = new SolidColorBrush(Color.FromRgb(204, 204, 204));
                }

                if (SettingsBackButton != null)
                {
                    SettingsBackButton.Background = new SolidColorBrush(Color.FromRgb(66, 66, 66));
                    SettingsBackButton.Foreground = new SolidColorBrush(Colors.White);
                }

                if (WiFiNetworkText != null)
                {
                    WiFiNetworkText.Foreground = new SolidColorBrush(Colors.White);
                }

                if (LocalIPText != null)
                {
                    LocalIPText.Foreground = new SolidColorBrush(Color.FromRgb(144, 202, 249));
                }
            }
            else
            {
                // Light Mode
                this.Background = new SolidColorBrush(Color.FromRgb(245, 245, 245)); // #F5F5F5
                DarkModeToggle.Content = "☀️ Light Mode";
                DarkModeToggle.Background = new SolidColorBrush(Color.FromRgb(66, 66, 66));

                // Main Menu
                if (MainMenuScreen != null && MainMenuScreen.Children.Count > 1)
                {
                    var title = MainMenuScreen.Children[1] as TextBlock;
                    if (title != null)
                    {
                        title.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                    }
                }

                // Host Screen - Light Mode
                if (BackButton != null)
                {
                    BackButton.Background = new SolidColorBrush(Color.FromRgb(224, 224, 224));
                    BackButton.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                }

                if (HostTitle != null)
                {
                    HostTitle.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                }

                if (PairingCodeLabel != null)
                {
                    PairingCodeLabel.Foreground = new SolidColorBrush(Color.FromRgb(97, 97, 97));
                }

                if (IPAddressText != null)
                {
                    IPAddressText.Foreground = new SolidColorBrush(Color.FromRgb(33, 150, 243)); // #2196F3
                }

                if (StatusBorder != null)
                {
                    StatusBorder.Background = new SolidColorBrush(Colors.White);
                    StatusBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(224, 224, 224));
                }

                if (StatusText != null)
                {
                    StatusText.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                }

                if (LogBorder != null)
                {
                    LogBorder.Background = new SolidColorBrush(Colors.White);
                    LogBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(224, 224, 224));
                }

                if (LogTitle != null)
                {
                    LogTitle.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                }

                if (ConnectedDevicesBorder != null)
                {
                    ConnectedDevicesBorder.Background = new SolidColorBrush(Color.FromRgb(250, 250, 250)); // #FAFAFA
                    ConnectedDevicesBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(224, 224, 224));
                }

                if (ConnectedDevicesTitle != null)
                {
                    ConnectedDevicesTitle.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                }

                if (ConnectionCountText != null)
                {
                    ConnectionCountText.Foreground = new SolidColorBrush(Color.FromRgb(97, 97, 97));
                }

                // Settings screen light mode
                if (SettingsBorder != null)
                {
                    SettingsBorder.Background = new SolidColorBrush(Colors.White);
                    SettingsBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(224, 224, 224));
                }

                if (SettingsTitle != null)
                {
                    SettingsTitle.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                }

                if (NotifyOnConnect != null)
                {
                    NotifyOnConnect.Foreground = new SolidColorBrush(Color.FromRgb(66, 66, 66));
                }

                if (NotifyOnDisconnect != null)
                {
                    NotifyOnDisconnect.Foreground = new SolidColorBrush(Color.FromRgb(66, 66, 66));
                }

                if (SettingsBackButton != null)
                {
                    SettingsBackButton.Background = new SolidColorBrush(Color.FromRgb(224, 224, 224));
                    SettingsBackButton.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                }

                if (WiFiNetworkText != null)
                {
                    WiFiNetworkText.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                }

                if (LocalIPText != null)
                {
                    LocalIPText.Foreground = new SolidColorBrush(Color.FromRgb(33, 150, 243));
                }
            }
        }

        private void ShowScreen(string screenName)
        {
            MainMenuScreen.Visibility = Visibility.Collapsed;
            HostScreen.Visibility = Visibility.Collapsed;
            SettingsScreen.Visibility = Visibility.Collapsed;

            switch (screenName)
            {
                case "MainMenu":
                    MainMenuScreen.Visibility = Visibility.Visible;
                    break;
                case "Host":
                    HostScreen.Visibility = Visibility.Visible;
                    break;
                case "Settings":
                    SettingsScreen.Visibility = Visibility.Visible;
                    break;
            }
        }

        // When the host button is clicked
        private void HostButton_Click(object sender, RoutedEventArgs e)
        {
            ShowScreen("Host");
            StartServer();
        }

        private void BackButton_Click(object sender, RoutedEventArgs e)
        {
            ShowScreen("MainMenu");
        }

        private void ExitButton_Click(object sender, RoutedEventArgs e)
        {
            Application.Current.Shutdown();
        }

        #endregion

        #region Server Methods

        // Start Server
        private void StartServer()
        {
            if (isServerRunning)
            {
                AddLog("⚠ Server already running");
                return;
            }

            localIP = GetLocalIPAddress();

            Random random = new Random();
            pairingCode = random.Next(1000, 9999).ToString();

            PairingCodeText.Text = pairingCode;
            IPAddressText.Text = $"IP: {localIP}:{serverPort}";

            // Create QR Code
            string qrData = $"{localIP}:{serverPort}:{pairingCode}";
            GenerateQRCode(qrData);

            try
            {
                udpServer = new UdpClient(serverPort);
                receiveThread = new Thread(new ThreadStart(ReceiveData));
                receiveThread.IsBackground = true;
                receiveThread.Start();
                isServerRunning = true;

                AddLog($"✓ Server started on {localIP}:{serverPort}");
                AddLog($"✓ Pairing Code: {pairingCode}");
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Failed to start server: {ex.Message}", "Error",
                    MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void GenerateQRCode(string data)
        {
            try
            {
                // Generate QR Image
                var writer = new ZXing.BarcodeWriter<System.Drawing.Bitmap>()
                {
                    Format = BarcodeFormat.QR_CODE,
                    Renderer = new BitmapRenderer(),
                    Options = new ZXing.Common.EncodingOptions
                    {
                        Width = 256,
                        Height = 256,
                        Margin = 1
                    }
                };

                using (var bitmap = writer.Write(data))
                {
                    using (var memory = new MemoryStream())
                    {
                        bitmap.Save(memory, ImageFormat.Png);
                        memory.Position = 0;

                        BitmapImage bitmapImage = new BitmapImage();
                        bitmapImage.BeginInit();
                        bitmapImage.StreamSource = memory;
                        bitmapImage.CacheOption = BitmapCacheOption.OnLoad;
                        bitmapImage.EndInit();
                        bitmapImage.Freeze();

                        // Show QR Code
                        QRCodeImage.Source = bitmapImage;
                    }
                }

                AddLog("✓ QR Code generated");
            }
            catch (Exception ex)
            {
                MessageBox.Show($"QR generation failed: {ex.Message}", "Error",
                    MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void ReceiveData()
        {
            while (isServerRunning)
            {
                try
                {
                    IPEndPoint remoteEndPoint = new IPEndPoint(IPAddress.Any, 0);
                    byte[] data = udpServer.Receive(ref remoteEndPoint);
                    string message = Encoding.UTF8.GetString(data);

                    Dispatcher.Invoke(() => ProcessMessage(message, remoteEndPoint));
                }
                catch (Exception ex)
                {
                    if (isServerRunning)
                    {
                        AddLog($"✗ Receive error: {ex.Message}");
                    }
                }
            }
        }

        // New: handle joystick input with state tracking per device to send down/up events
        private void HandleJoystick(Dictionary<string, object> msg, string senderIP)
        {
            if (!msg.ContainsKey("x") || !msg.ContainsKey("y"))
                return;

            float x = 0f;
            float y = 0f;

            try
            {
                // Json.NET may decode numbers as long/double; Convert.ToSingle handles both
                x = Convert.ToSingle(msg["x"]);
                y = Convert.ToSingle(msg["y"]);
            }
            catch
            {
                AddLog($"⚠ Invalid joystick values from {senderIP}");
                return;
            }

            const float DEADZONE = 0.4f;

            bool wantUp = y > DEADZONE;
            bool wantDown = y < -DEADZONE;
            bool wantRight = x > DEADZONE;
            bool wantLeft = x < -DEADZONE;

            if (!deviceJoystickStates.ContainsKey(senderIP))
                deviceJoystickStates[senderIP] = new JoystickState();

            var state = deviceJoystickStates[senderIP];

            // Helper to toggle directional keys with down/up events
            void UpdateDir(bool want, bool current, string name)
            {
                if (want && !current)
                {
                    SimulateKeyPress($"{name}_down", senderIP);
                    state.Set(name, true);
                }
                else if (!want && current)
                {
                    SimulateKeyPress($"{name}_up", senderIP);
                    state.Set(name, false);
                }
            }

            UpdateDir(wantUp, state.Up, "up");
            UpdateDir(wantDown, state.Down, "down");
            UpdateDir(wantLeft, state.Left, "left");
            UpdateDir(wantRight, state.Right, "right");

            AddLog($"🎮 Joystick ({senderIP}) x={x:0.00}, y={y:0.00}");
        }

        // New: handle right joystick -> mouse movement using Win32 SetCursorPos/GetCursorPos
        private void HandleMouse(Dictionary<string, object> msg, string senderIP)
        {
            if (!msg.ContainsKey("x") || !msg.ContainsKey("y"))
                return;

            float x = 0f;
            float y = 0f;

            try
            {
                x = Convert.ToSingle(msg["x"]);
                y = Convert.ToSingle(msg["y"]);
            }
            catch
            {
                AddLog($"⚠ Invalid mouse values from {senderIP}");
                return;
            }

            const float DEADZONE = 0.15f; // smaller deadzone for smoother mouse
            const double SENSITIVITY = 18.0; // tweakable multiplier

            if (!deviceMouseStates.ContainsKey(senderIP))
                deviceMouseStates[senderIP] = new MouseState();

            var state = deviceMouseStates[senderIP];

            // zero small jitter
            double vx = (Math.Abs(x) < DEADZONE) ? 0.0 : x;
            double vy = (Math.Abs(y) < DEADZONE) ? 0.0 : y;

            // Android joystick's y is already inverted in the client (y = -deltaY / radius),
            // so we negate here so positive y moves mouse downwards.
            double moveX = vx * SENSITIVITY;
            double moveY = -vy * SENSITIVITY;

            state.Accumulate(moveX, moveY);

            // take integer parts to move the mouse, keep fractional remainder
            int dx = state.TakeIntegerX();
            int dy = state.TakeIntegerY();

            if (dx != 0 || dy != 0)
            {
                try
                {
                    // Read current cursor position
                    if (GetCursorPos(out POINT currentPos))
                    {
                        int screenWidth = (int)SystemParameters.PrimaryScreenWidth;
                        int screenHeight = (int)SystemParameters.PrimaryScreenHeight;

                        int newX = currentPos.X + dx;
                        int newY = currentPos.Y + dy;

                        // Clamp to screen bounds
                        newX = Math.Max(0, Math.Min(screenWidth - 1, newX));
                        newY = Math.Max(0, Math.Min(screenHeight - 1, newY));

                        SetCursorPos(newX, newY);
                    }
                }
                catch (Exception ex)
                {
                    AddLog($"✗ Mouse move failed: {ex.Message}");
                }
            }

            // Log occasional values for debugging (rate-limited by only logging when movement is significant)
            if (Math.Abs(moveX) > 0.5 || Math.Abs(moveY) > 0.5)
                AddLog($"🖱️ Mouse ({senderIP}) raw x={x:0.00}, y={y:0.00} → dx={dx}, dy={dy}");
        }

        private void ProcessMessage(string message, IPEndPoint remoteEndPoint)
        {
            try
            {
                var jsonData = JsonConvert.DeserializeObject<Dictionary<string, object>>(message);

                if (!jsonData.ContainsKey("action"))
                {
                    AddLog($"⚠ Invalid message format from {remoteEndPoint.Address}");
                    return;
                }

                string action = jsonData["action"].ToString();

                if (action == "discover")
                {
                    AddLog($"📱 Discovery request from {remoteEndPoint.Address}");
                    SendDiscoveryResponse(remoteEndPoint);
                }
                else if (action == "pair")
                {
                    string receivedCode = jsonData.ContainsKey("code") ? jsonData["code"].ToString() : "";

                    if (receivedCode == pairingCode)
                    {
                        string deviceIP = remoteEndPoint.Address.ToString();

                        if (!deviceEndpoints.ContainsKey(deviceIP))
                        {
                            deviceEndpoints[deviceIP] = remoteEndPoint;

                            // Try both "device" and "deviceName" fields
                            string deviceNameFromMessage = null;
                            if (jsonData.ContainsKey("device"))
                            {
                                deviceNameFromMessage = jsonData["device"].ToString();
                            }
                            else if (jsonData.ContainsKey("deviceName"))
                            {
                                deviceNameFromMessage = jsonData["deviceName"].ToString();
                            }

                            var newDevice = new ConnectedDevice
                            {
                                Name = deviceNameFromMessage ?? "Unknown Device",
                                IPAddress = deviceIP,
                                Status = "Connected"
                            };

                            connectedDevices.Add(newDevice);
                            ConnectionCountText.Text = $"{connectedDevices.Count} device{(connectedDevices.Count != 1 ? "s" : "")} connected";

                            StatusText.Text = $"({connectedDevices.Count}) device{(connectedDevices.Count != 1 ? "s" : "")} connected";
                            StatusText.Foreground = new SolidColorBrush(Color.FromRgb(76, 175, 80));

                            AddLog($"✓ {newDevice.Name} paired successfully ({deviceIP})");

                            SendPairSuccessResponse(remoteEndPoint);
                            SendConfigurationToDevice(deviceIP);

                            if (NotifyOnConnect?.IsChecked == true)
                            {
                                MessageBox.Show($"{newDevice.Name} has connected!", "Device Connected",
                                    MessageBoxButton.OK, MessageBoxImage.Information);
                            }
                        }
                        else
                        {
                            AddLog($"⚠ Device {deviceIP} already connected");
                            // Still send success response
                            SendPairSuccessResponse(remoteEndPoint);
                        }
                    }
                    else
                    {
                        AddLog($"✗ Invalid pairing code from {remoteEndPoint.Address}");
                        SendPairFailedResponse(remoteEndPoint);
                    }
                }
                else if (action == "input")
                {
                    // New input format: { "action": "input", "input": "w_down", "device": "..." }
                    if (jsonData.ContainsKey("input"))
                    {
                        string inputAction = jsonData["input"].ToString();
                        SimulateKeyPress(inputAction, remoteEndPoint.Address.ToString());
                    }
                    else
                    {
                        AddLog($"⚠ Empty input from {remoteEndPoint.Address}");
                    }
                }
                else if (action == "joystick")
                {
                    // Pass the full parsed message to HandleJoystick so it can read x/y
                    HandleJoystick(jsonData, remoteEndPoint.Address.ToString());
                }
                else if (action == "mouse")
                {
                    // Right joystick -> mouse movement
                    HandleMouse(jsonData, remoteEndPoint.Address.ToString());
                }
                // ===== FLIGHT CONTROLLER HANDLERS (restored) =====
                else if (action == "gyro_data")
                {
                    // Keep gyro_data for logging / future processing
                    AddLog($"📡 Gyro data received from {remoteEndPoint.Address}");
                }
                else if (action == "key")
                {
                    HandleKeyInput(jsonData, remoteEndPoint.Address.ToString());
                }
                else if (action == "mouse_move")
                {
                    HandleMouseMove(jsonData, remoteEndPoint.Address.ToString());
                }
                else if (action == "mouse_button")
                {
                    HandleMouseButton(jsonData, remoteEndPoint.Address.ToString());
                }
                // ===== END FLIGHT CONTROLLER HANDLERS =====
                else
                {
                    // Legacy format: action is the key input directly (e.g., "up_down")
                    SimulateKeyPress(action, remoteEndPoint.Address.ToString());
                }
            }
            catch (Exception ex)
            {
                AddLog($"✗ Message processing error: {ex.Message}");
            }
        }

        private void SendDiscoveryResponse(IPEndPoint endpoint)
        {
            var response = new
            {
                action = "discovered",
                serverName = "MobControl Desktop",
                ip = localIP,
                port = serverPort
            };

            string json = JsonConvert.SerializeObject(response);
            byte[] data = Encoding.UTF8.GetBytes(json);

            try
            {
                udpServer.Send(data, data.Length, endpoint);
            }
            catch (Exception ex)
            {
                AddLog($"✗ Failed to send discovery response: {ex.Message}");
            }
        }

        private void SendPairSuccessResponse(IPEndPoint endpoint)
        {
            var response = new
            {
                status = "connected",
                action = "pair_success",
                message = "Connected successfully!"
            };

            string json = JsonConvert.SerializeObject(response);
            byte[] data = Encoding.UTF8.GetBytes(json);

            try
            {
                udpServer.Send(data, data.Length, endpoint);
                AddLog($"✓ Sent pairing success to {endpoint.Address}");
            }
            catch (Exception ex)
            {
                AddLog($"✗ Failed to send pair success: {ex.Message}");
            }
        }

        private void SendPairFailedResponse(IPEndPoint endpoint)
        {
            var response = new
            {
                action = "pair_failed",
                message = "Invalid pairing code"
            };

            string json = JsonConvert.SerializeObject(response);
            byte[] data = Encoding.UTF8.GetBytes(json);

            try
            {
                udpServer.Send(data, data.Length, endpoint);
            }
            catch (Exception ex)
            {
                AddLog($"✗ Failed to send pair failed: {ex.Message}");
            }
        }

        private object CreateButtonConfigForMobile()
        {
            var buttonConfig = new Dictionary<string, object>();

            foreach (var kvp in buttonMappings)
            {
                buttonConfig[kvp.Key] = new
                {
                    enabled = kvp.Value.Enabled,
                    key = kvp.Value.Key
                };
            }

            return buttonConfig;
        }

        private object CreateButtonConfigForMobile(Dictionary<string, ButtonMapping> mappings)
        {
            var buttonConfig = new Dictionary<string, object>();

            foreach (var kvp in mappings)
            {
                buttonConfig[kvp.Key] = new
                {
                    enabled = kvp.Value.Enabled,
                    key = kvp.Value.Key
                };
            }

            return buttonConfig;
        }

        private void SimulateKeyPress(string action, string senderIP = null)
        {
            // Parse action format: "up_down", "up_up", or just "up" (legacy)
            string buttonName = action;
            string eventType = "press"; // default for legacy support

            if (action.Contains("_"))
            {
                string[] parts = action.Split('_');
                if (parts.Length == 2)
                {
                    buttonName = parts[0];
                    eventType = parts[1]; // "down" or "up"
                }
            }

            // Get device-specific mapping if exists, otherwise use default
            var mappings = (!string.IsNullOrEmpty(senderIP) && deviceMappings.ContainsKey(senderIP))
                ? deviceMappings[senderIP]
                : buttonMappings;

            if (!mappings.ContainsKey(buttonName))
            {
                AddLog($"⚠ Unknown action: {buttonName}");
                return;
            }

            var mapping = mappings[buttonName];

            if (!mapping.Enabled)
            {
                AddLog($"⚠ Button disabled: {buttonName}");
                return;
            }

            if (mapping.VirtualKey == VirtualKeyCode.NONAME)
            {
                AddLog($"⚠ Invalid key for {buttonName}");
                return;
            }

            // Handle key down/up events separately for games
            if (eventType == "down")
            {
                inputSimulator.Keyboard.KeyDown(mapping.VirtualKey);
                string deviceInfo = !string.IsNullOrEmpty(senderIP) ? $" (from {senderIP})" : "";
                AddLog($"▼ {buttonName.ToUpper()} → {mapping.Key} DOWN{deviceInfo}");
            }
            else if (eventType == "up")
            {
                inputSimulator.Keyboard.KeyUp(mapping.VirtualKey);
                string deviceInfo = !string.IsNullOrEmpty(senderIP) ? $" (from {senderIP})" : "";
                AddLog($"▲ {buttonName.ToUpper()} → {mapping.Key} UP{deviceInfo}");
            }
            else // "press" - legacy support (single press)
            {
                inputSimulator.Keyboard.KeyPress(mapping.VirtualKey);
                string deviceInfo = !string.IsNullOrEmpty(senderIP) ? $" (from {senderIP})" : "";
                AddLog($"✓ {buttonName.ToUpper()} → {mapping.Key}{deviceInfo}");
            }
        }

        #endregion

        #region Flight Controller Support (RESTORED HANDLERS)

        // Restored from original project: key/mouse move/button handlers used by the flight controller client.
        private void HandleKeyInput(Dictionary<string, object> data, string senderIP)
        {
            try
            {
                if (!data.ContainsKey("key") || !data.ContainsKey("pressed"))
                {
                    AddLog($"⚠ Invalid key input data from {senderIP}");
                    return;
                }

                string key = data["key"].ToString().ToLower();
                bool pressed = Convert.ToBoolean(data["pressed"]);

                VirtualKeyCode virtualKey;
                string keyDisplayName;

                switch (key)
                {
                    case "w":
                        virtualKey = VirtualKeyCode.VK_W;
                        keyDisplayName = "W (Forward)";
                        break;
                    case "s":
                        virtualKey = VirtualKeyCode.VK_S;
                        keyDisplayName = "S (Backward)";
                        break;
                    case "a":
                        virtualKey = VirtualKeyCode.VK_A;
                        keyDisplayName = "A (Left)";
                        break;
                    case "d":
                        virtualKey = VirtualKeyCode.VK_D;
                        keyDisplayName = "D (Right)";
                        break;
                    case "shift":
                        virtualKey = VirtualKeyCode.SHIFT;
                        keyDisplayName = "SHIFT (Turbo)";
                        break;
                    case "p":
                        virtualKey = VirtualKeyCode.VK_P;
                        keyDisplayName = "P (Pause)";
                        break;
                    default:
                        AddLog($"⚠ Unknown key: {key}");
                        return;
                }

                if (pressed)
                {
                    inputSimulator.Keyboard.KeyDown(virtualKey);
                    AddLog($"🔽 {keyDisplayName} PRESSED");
                }
                else
                {
                    inputSimulator.Keyboard.KeyUp(virtualKey);
                    AddLog($"🔼 {keyDisplayName} RELEASED");
                }
            }
            catch (Exception ex)
            {
                AddLog($"✗ Key input error: {ex.Message}");
            }
        }

        private void HandleMouseMove(Dictionary<string, object> data, string senderIP)
        {
            try
            {
                if (!data.ContainsKey("x") || !data.ContainsKey("y"))
                {
                    AddLog($"⚠ Invalid mouse move data from {senderIP}");
                    return;
                }

                int deltaX = Convert.ToInt32(data["x"]);
                int deltaY = Convert.ToInt32(data["y"]);

                // Get current mouse position using Win32 API
                POINT currentPos;
                if (!GetCursorPos(out currentPos))
                {
                    AddLog($"⚠ Unable to read cursor position for {senderIP}");
                    return;
                }

                // Apply mouse movement (relative)
                int newX = currentPos.X + deltaX;
                int newY = currentPos.Y + deltaY;

                // Get screen dimensions
                int screenWidth = (int)SystemParameters.PrimaryScreenWidth;
                int screenHeight = (int)SystemParameters.PrimaryScreenHeight;

                // Clamp to screen bounds
                newX = Math.Max(0, Math.Min(screenWidth - 1, newX));
                newY = Math.Max(0, Math.Min(screenHeight - 1, newY));

                // Move mouse cursor using Win32 API
                SetCursorPos(newX, newY);

                // Log less frequently
                if (Math.Abs(deltaX) > 50 || Math.Abs(deltaY) > 50)
                {
                    AddLog($"🎯 Mouse moved (flight controller): Δ({deltaX}, {deltaY})");
                }
            }
            catch (Exception ex)
            {
                AddLog($"✗ Mouse move error: {ex.Message}");
            }
        }

        private void HandleMouseButton(Dictionary<string, object> data, string senderIP)
        {
            try
            {
                if (!data.ContainsKey("button") || !data.ContainsKey("pressed"))
                {
                    AddLog($"⚠ Invalid mouse button data from {senderIP}");
                    return;
                }

                string button = data["button"].ToString().ToLower();
                bool pressed = Convert.ToBoolean(data["pressed"]);

                if (button == "left")
                {
                    if (pressed)
                    {
                        inputSimulator.Mouse.LeftButtonDown();
                        AddLog($"🔫 LEFT MOUSE DOWN (Machine Gun)");
                    }
                    else
                    {
                        inputSimulator.Mouse.LeftButtonUp();
                        AddLog($"🔫 LEFT MOUSE UP");
                    }
                }
                else if (button == "right")
                {
                    if (pressed)
                    {
                        inputSimulator.Mouse.RightButtonDown();
                        AddLog($"🚀 RIGHT MOUSE DOWN (Rockets)");
                    }
                    else
                    {
                        inputSimulator.Mouse.RightButtonUp();
                        AddLog($"🚀 RIGHT MOUSE UP");
                    }
                }
                else
                {
                    AddLog($"⚠ Unknown mouse button: {button}");
                }
            }
            catch (Exception ex)
            {
                AddLog($"✗ Mouse button error: {ex.Message}");
            }
        }

        #endregion

        #region Network Methods

        private string GetLocalIPAddress()
        {
            string localIP = "127.0.0.1";

            try
            {
                var host = Dns.GetHostEntry(Dns.GetHostName());

                // Prioritize common WiFi/LAN ranges, avoid virtual adapters
                foreach (var ip in host.AddressList)
                {
                    if (ip.AddressFamily == AddressFamily.InterNetwork)
                    {
                        string ipStr = ip.ToString();

                        // Skip loopback
                        if (ipStr.StartsWith("127."))
                            continue;

                        // Skip APIPA (Automatic Private IP Addressing)
                        if (ipStr.StartsWith("169.254."))
                            continue;

                        // Skip VirtualBox Host-Only Adapter (common: 192.168.56.x)
                        if (ipStr.StartsWith("192.168.56."))
                            continue;

                        // Skip VMware Host-Only Adapter (common: 192.168.xx.x where xx > 100)
                        if (ipStr.StartsWith("192.168.") &&
                            int.TryParse(ipStr.Split('.')[2], out int thirdOctet) &&
                            thirdOctet >= 50)
                        {
                            // Skip likely virtual adapter ranges (50-99, 100-254)
                            // But keep common router ranges (0-49)
                            if (thirdOctet > 49)
                                continue;
                        }

                        // Prioritize 192.168.0.x and 192.168.1.x (most common home/office WiFi)
                        if (ipStr.StartsWith("192.168.0.") || ipStr.StartsWith("192.168.1."))
                        {
                            AddLog($"✓ Detected WiFi IP: {ipStr}");
                            return ipStr;
                        }

                        // Accept 192.168.2-49.x (other common router ranges)
                        if (ipStr.StartsWith("192.168."))
                        {
                            localIP = ipStr;
                            AddLog($"✓ Detected LAN IP: {ipStr}");
                        }
                        // Accept 10.x.x.x (corporate networks)
                        else if (ipStr.StartsWith("10."))
                        {
                            if (localIP == "127.0.0.1")
                                localIP = ipStr;
                            AddLog($"✓ Detected corporate IP: {ipStr}");
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                AddLog($"✗ Failed to get IP: {ex.Message}");
            }

            if (localIP == "127.0.0.1")
            {
                AddLog("⚠ Warning: No valid network IP found, using loopback");
            }

            return localIP;
        }

        #endregion

        #region Device Management

        private void DisconnectDevice_Click(object sender, RoutedEventArgs e)
        {
            if (sender is System.Windows.Controls.Button button)
            {
                string deviceIP = button.Tag as string;

                if (!string.IsNullOrEmpty(deviceIP))
                {
                    var device = connectedDevices.FirstOrDefault(d => d.IPAddress == deviceIP);

                    if (device != null)
                    {
                        connectedDevices.Remove(device);
                        deviceEndpoints.Remove(deviceIP);

                        ConnectionCountText.Text = $"{connectedDevices.Count} device{(connectedDevices.Count != 1 ? "s" : "")} connected";

                        // Update status text
                        if (connectedDevices.Count > 0)
                        {
                            StatusText.Text = $"({connectedDevices.Count}) device{(connectedDevices.Count != 1 ? "s" : "")} connected";
                            StatusText.Foreground = new SolidColorBrush(Color.FromRgb(76, 175, 80)); // Green
                        }
                        else
                        {
                            StatusText.Text = "⏳ Waiting for connection...";
                            StatusText.Foreground = new SolidColorBrush(Color.FromRgb(255, 193, 7)); // Yellow
                        }

                        AddLog($"✓ {device.Name} disconnected");

                        if (NotifyOnDisconnect?.IsChecked == true)
                        {
                            MessageBox.Show($"{device.Name} has been disconnected.", "Device Disconnected",
                                MessageBoxButton.OK, MessageBoxImage.Information);
                        }
                    }
                }
            }
        }

        #endregion

        #region Settings Methods

        private void SettingsButton_Click(object sender, RoutedEventArgs e)
        {
            ShowScreen("Settings");
            UpdateSettingsNetworkInfo();
        }

        private void UpdateSettingsNetworkInfo()
        {
            // Update WiFi Network Name
            try
            {
                string networkName = GetCurrentWiFiNetworkName();
                WiFiNetworkText.Text = string.IsNullOrEmpty(networkName) ? "Not Connected" : networkName;
            }
            catch
            {
                WiFiNetworkText.Text = "Unable to detect";
            }

            // Update Local IP
            LocalIPText.Text = string.IsNullOrEmpty(localIP) ? "127.0.0.1" : localIP;

            // Update Mobile Connection Status
            int deviceCount = connectedDevices.Count;

            if (deviceCount > 0)
            {
                MobileConnectionIndicator.Fill = new SolidColorBrush(Color.FromRgb(76, 175, 80)); // Green
                MobileConnectionText.Text = "Connected";
                MobileConnectionText.Foreground = new SolidColorBrush(Color.FromRgb(76, 175, 80));
                ConnectedDevicesCountText.Text = $"{deviceCount} mobile device{(deviceCount != 1 ? "s" : "")} connected";
                ConnectedDevicesCountText.Foreground = new SolidColorBrush(Color.FromRgb(76, 175, 80));
            }
            else
            {
                MobileConnectionIndicator.Fill = new SolidColorBrush(Color.FromRgb(117, 117, 117)); // Gray
                MobileConnectionText.Text = "No devices connected";
                MobileConnectionText.Foreground = new SolidColorBrush(Color.FromRgb(170, 170, 170));
                ConnectedDevicesCountText.Text = "0 mobile devices connected";
                ConnectedDevicesCountText.Foreground = new SolidColorBrush(Color.FromRgb(117, 117, 117));
            }
        }

        private string GetCurrentWiFiNetworkName()
        {
            try
            {
                // Try to get WiFi network name using netsh command
                var process = new System.Diagnostics.Process
                {
                    StartInfo = new System.Diagnostics.ProcessStartInfo
                    {
                        FileName = "netsh",
                        Arguments = "wlan show interfaces",
                        RedirectStandardOutput = true,
                        UseShellExecute = false,
                        CreateNoWindow = true
                    }
                };

                process.Start();
                string output = process.StandardOutput.ReadToEnd();
                process.WaitForExit();

                // Parse SSID from output
                var lines = output.Split('\n');
                foreach (var line in lines)
                {
                    if (line.Trim().StartsWith("SSID") && !line.Contains("BSSID"))
                    {
                        var parts = line.Split(':');
                        if (parts.Length >= 2)
                        {
                            return parts[1].Trim();
                        }
                    }
                }
            }
            catch { }

            return "Unknown Network";
        }

        private void ViewConnectionLogsButton_Click(object sender, RoutedEventArgs e)
        {
            // Switch to host screen to show logs
            ShowScreen("Host");
            AddLog("✓ Viewing connection logs");
        }

        private void EditControllerMappingButton_Click(object sender, RoutedEventArgs e)
        {
            // Open the Button Mapping Editor window
            var mappingWindow = new ButtonMappingWindow(buttonMappings);
            mappingWindow.Owner = this;

            bool? result = mappingWindow.ShowDialog();

            if (result == true && mappingWindow.MappingsChanged)
            {
                // Get the updated mappings
                buttonMappings = mappingWindow.GetUpdatedMappings();

                // Log the update
                AddLog("✓ Button mappings updated");

                // Send updated configuration to all connected devices
                SendConfigurationToAllDevices();

                MessageBox.Show(
                    "Button mappings have been saved successfully!\n\nThe new configuration has been sent to all connected mobile devices.",
                    "Success",
                    MessageBoxButton.OK,
                    MessageBoxImage.Information);
            }
        }

        private void SendConfigurationToAllDevices()
        {
            if (deviceEndpoints.Count == 0)
            {
                return;
            }

            var config = new
            {
                action = "update_config",
                buttons = CreateButtonConfigForMobile()
            };

            string configMessage = JsonConvert.SerializeObject(config);
            byte[] configData = Encoding.UTF8.GetBytes(configMessage);

            foreach (var endpoint in deviceEndpoints.Values)
            {
                try
                {
                    udpServer.Send(configData, configData.Length, endpoint);
                }
                catch (Exception ex)
                {
                    AddLog($"✗ Failed to send config to {endpoint.Address}: {ex.Message}");
                }
            }

            AddLog($"✓ Configuration sent to {deviceEndpoints.Count} device(s)");
        }

        private void LoadDeviceMappings()
        {
            try
            {
                string filePath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "device_mappings.json");
                if (File.Exists(filePath))
                {
                    string json = File.ReadAllText(filePath);
                    var loaded = JsonConvert.DeserializeObject<Dictionary<string, Dictionary<string, ButtonMapping>>>(json);
                    if (loaded != null)
                    {
                        deviceMappings = loaded;
                    }
                }
            }
            catch (Exception ex)
            {
                AddLog($"Failed to load device mappings: {ex.Message}");
            }
        }

        private void SaveDeviceMappings()
        {
            try
            {
                string filePath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "device_mappings.json");
                string json = JsonConvert.SerializeObject(deviceMappings, Formatting.Indented);
                File.WriteAllText(filePath, json);
                AddLog("✓ Device mappings saved");
            }
            catch (Exception ex)
            {
                AddLog($"✗ Failed to save device mappings: {ex.Message}");
            }
        }

        private void EditDeviceMapping_Click(object sender, RoutedEventArgs e)
        {
            Button button = sender as Button;
            if (button == null) return;

            string deviceIP = button.Tag as string;
            if (string.IsNullOrEmpty(deviceIP)) return;

            // Get device name
            var device = connectedDevices.FirstOrDefault(d => d.IPAddress == deviceIP);
            if (device == null) return;

            // Get device-specific mapping or clone from default
            Dictionary<string, ButtonMapping> deviceMapping;

            if (deviceMappings.ContainsKey(deviceIP))
            {
                deviceMapping = deviceMappings[deviceIP];
            }
            else
            {
                // Clone default mappings for this device
                deviceMapping = CloneButtonMappings(buttonMappings);
                deviceMappings[deviceIP] = deviceMapping;
            }

            // Open mapping window with device-specific title
            var mappingWindow = new ButtonMappingWindow(deviceMapping);
            mappingWindow.Title = $"Edit Controller Mapping - {device.Name}";
            mappingWindow.Owner = this;

            bool? result = mappingWindow.ShowDialog();

            if (result == true && mappingWindow.MappingsChanged)
            {
                // Get the updated mappings
                deviceMappings[deviceIP] = mappingWindow.GetUpdatedMappings();

                // Save device mappings to file
                SaveDeviceMappings();

                // Log the update
                AddLog($"✓ Button mappings updated for {device.Name}");

                // Send updated configuration to this specific device
                SendConfigurationToDevice(deviceIP);

                MessageBox.Show(
                    $"Button mappings for {device.Name} have been saved successfully!\n\nThe new configuration has been sent to the device.",
                    "Success",
                    MessageBoxButton.OK,
                    MessageBoxImage.Information);
            }
        }

        private Dictionary<string, ButtonMapping> CloneButtonMappings(Dictionary<string, ButtonMapping> source)
        {
            var clone = new Dictionary<string, ButtonMapping>();
            foreach (var kvp in source)
            {
                clone[kvp.Key] = new ButtonMapping
                {
                    Enabled = kvp.Value.Enabled,
                    Key = kvp.Value.Key,
                    VirtualKey = kvp.Value.VirtualKey
                };
            }
            return clone;
        }

        private void SendConfigurationToDevice(string deviceIP)
        {
            if (!deviceEndpoints.ContainsKey(deviceIP))
            {
                return;
            }

            var endpoint = deviceEndpoints[deviceIP];
            var mapping = deviceMappings.ContainsKey(deviceIP) ? deviceMappings[deviceIP] : buttonMappings;

            var config = new
            {
                action = "update_config",
                buttons = CreateButtonConfigForMobile(mapping)
            };

            string configMessage = JsonConvert.SerializeObject(config);
            byte[] configData = Encoding.UTF8.GetBytes(configMessage);

            try
            {
                udpServer.Send(configData, configData.Length, endpoint);
                AddLog($"✓ Configuration sent to {deviceIP}");
            }
            catch (Exception ex)
            {
                AddLog($"✗ Failed to send config to {deviceIP}: {ex.Message}");
            }
        }

        #endregion

        #region Logging

        private void AddLog(string message)
        {
            string timestamp = DateTime.Now.ToString("HH:mm:ss");
            string logMessage = $"[{timestamp}] {message}\n";

            LogText.Text = logMessage + LogText.Text;

            var lines = LogText.Text.Split('\n');
            if (lines.Length > 50)
            {
                LogText.Text = string.Join("\n", lines.Take(50));
            }
        }

        #endregion

        protected override void OnClosed(EventArgs e)
        {
            isServerRunning = false;

            if (receiveThread != null && receiveThread.IsAlive)
            {
                receiveThread.Interrupt();
            }

            if (udpServer != null)
            {
                udpServer.Close();
            }

            base.OnClosed(e);
        }

        // State holder for joystick per device
        private class JoystickState
        {
            public bool Up { get; private set; }
            public bool Down { get; private set; }
            public bool Left { get; private set; }
            public bool Right { get; private set; }

            public void Set(string direction, bool value)
            {
                switch (direction)
                {
                    case "up": Up = value; break;
                    case "down": Down = value; break;
                    case "left": Left = value; break;
                    case "right": Right = value; break;
                }
            }
        }

        // State holder for fractional mouse movement per device
        private class MouseState
        {
            private double accX = 0.0;
            private double accY = 0.0;
            private readonly object _lock = new object();

            public void Accumulate(double dx, double dy)
            {
                lock (_lock)
                {
                    accX += dx;
                    accY += dy;
                }
            }

            public int TakeIntegerX()
            {
                lock (_lock)
                {
                    int ix = (int)Math.Truncate(accX);
                    accX -= ix;
                    return ix;
                }
            }

            public int TakeIntegerY()
            {
                lock (_lock)
                {
                    int iy = (int)Math.Truncate(accY);
                    accY -= iy;
                    return iy;
                }
            }
        }
    }

    #region Data Models

    public class InputMessage
    {
        public string action { get; set; }
        public string code { get; set; }
        public string deviceName { get; set; }
        public string input { get; set; }
        public string device { get; set; }
    }

    public class ButtonMapping
    {
        public bool Enabled { get; set; }
        public string Key { get; set; }
        public VirtualKeyCode VirtualKey { get; set; }
    }

    public class ConnectedDevice
    {
        public string Name { get; set; }
        public string IPAddress { get; set; }
        public string Status { get; set; }
    }

    #endregion
}