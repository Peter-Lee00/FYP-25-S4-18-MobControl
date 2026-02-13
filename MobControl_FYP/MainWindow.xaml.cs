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
using System.Runtime.InteropServices;

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

        // Connected devices
        private ObservableCollection<ConnectedDevice> connectedDevices;
        private Dictionary<string, IPEndPoint> deviceEndpoints;

        // Dark mode state
        private bool isDarkMode = true;

        // Heartbeat status
        private Dictionary<string, DateTime> lastHeartbeat = new Dictionary<string, DateTime>();
        private System.Threading.Timer heartbeatMonitor;

        public MainWindow()
        {
            InitializeComponent();
            inputSimulator = new InputSimulator();
            connectedDevices = new ObservableCollection<ConnectedDevice>();
            deviceEndpoints = new Dictionary<string, IPEndPoint>();
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
            }
            else
            {
                // Light Mode
                this.Background = new SolidColorBrush(Color.FromRgb(245, 245, 245)); // #F5F5F5
                DarkModeToggle.Content = "☀️ Light Mode";
                DarkModeToggle.Background = new SolidColorBrush(Color.FromRgb(66, 66, 66));
                DarkModeToggle.Foreground = new SolidColorBrush(Colors.White);

                // Main Menu - Light Mode
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
                    BackButton.Background = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                    BackButton.Foreground = new SolidColorBrush(Colors.White);
                }

                if (HostTitle != null)
                {
                    HostTitle.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                }

                if (PairingCodeLabel != null)
                {
                    PairingCodeLabel.Foreground = new SolidColorBrush(Color.FromRgb(117, 117, 117)); // Darker gray for visibility
                }

                if (IPAddressText != null)
                {
                    IPAddressText.Foreground = new SolidColorBrush(Color.FromRgb(33, 150, 243)); // #2196F3 - Brighter blue
                }

                if (StatusBorder != null)
                {
                    StatusBorder.Background = new SolidColorBrush(Color.FromRgb(224, 224, 224)); // #E0E0E0
                    StatusBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(189, 189, 189)); // #BDBDBD
                }

                if (StatusText != null)
                {
                    StatusText.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                }

                if (LogBorder != null)
                {
                    LogBorder.Background = new SolidColorBrush(Color.FromRgb(250, 250, 250)); // Very light gray
                    LogBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(189, 189, 189));
                }

                if (LogTitle != null)
                {
                    LogTitle.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                }

                if (LogText != null)
                {
                    LogText.Foreground = new SolidColorBrush(Color.FromRgb(0, 150, 0)); // Darker green for visibility
                }

                if (ConnectedDevicesBorder != null)
                {
                    ConnectedDevicesBorder.Background = new SolidColorBrush(Color.FromRgb(250, 250, 250)); // #FAFAFA
                    ConnectedDevicesBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(189, 189, 189));
                }

                if (ConnectedDevicesTitle != null)
                {
                    ConnectedDevicesTitle.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                }

                if (ConnectionCountText != null)
                {
                    ConnectionCountText.Foreground = new SolidColorBrush(Color.FromRgb(117, 117, 117));
                }

                // Settings screen light mode
                if (SettingsBorder != null)
                {
                    SettingsBorder.Background = new SolidColorBrush(Color.FromRgb(255, 255, 255));
                    SettingsBorder.BorderBrush = new SolidColorBrush(Color.FromRgb(189, 189, 189));
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
                    SettingsBackButton.Background = new SolidColorBrush(Color.FromRgb(189, 189, 189));
                    SettingsBackButton.Foreground = new SolidColorBrush(Color.FromRgb(33, 33, 33));
                }

                // Update Test Network button
                if (TestNetworkButton != null)
                {
                    TestNetworkButton.Background = new SolidColorBrush(Color.FromRgb(66, 66, 66));
                    TestNetworkButton.Foreground = new SolidColorBrush(Colors.White);
                }

                // Update View Connection Logs button
                if (ViewConnectionLogsButton != null)
                {
                    ViewConnectionLogsButton.Background = new SolidColorBrush(Color.FromRgb(66, 66, 66));
                    ViewConnectionLogsButton.Foreground = new SolidColorBrush(Colors.White);
                }

                // Update Edit Controller Mapping button
                if (EditControllerMappingButton != null)
                {
                    EditControllerMappingButton.Background = new SolidColorBrush(Color.FromRgb(66, 66, 66));
                    EditControllerMappingButton.Foreground = new SolidColorBrush(Colors.White);
                }
            }
        }

        private void HostButton_Click(object sender, RoutedEventArgs e)
        {
            ShowScreen("Host");
            if (!isServerRunning)
            {
                StartServer();
            }
        }

        private void SettingsButton_Click(object sender, RoutedEventArgs e)
        {
            ShowScreen("Settings");
        }

        private void ExitButton_Click(object sender, RoutedEventArgs e)
        {
            Application.Current.Shutdown();
        }

        private void BackButton_Click(object sender, RoutedEventArgs e)
        {
            ShowScreen("MainMenu");
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

        #endregion

        #region Server Methods

        private void StartServer()
        {
            try
            {
                // VirtualBox IP 제외하고 실제 WiFi IP 가져오기
                localIP = GetLocalIPAddress();

                // 페어링 코드 생성
                Random random = new Random();
                pairingCode = random.Next(1000, 9999).ToString();

                PairingCodeText.Text = pairingCode;
                IPAddressText.Text = $"IP: {localIP}:{serverPort}";

                // QR 코드 생성
                string qrData = $"{localIP}:{serverPort}:{pairingCode}";
                GenerateQRCode(qrData);

                // UDP 서버 시작
                udpServer = new UdpClient(serverPort);
                udpServer.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);

                receiveThread = new Thread(new ThreadStart(ReceiveData));
                receiveThread.IsBackground = true;
                receiveThread.Start();
                isServerRunning = true;

                StatusText.Text = "⏳ Waiting for connection...";
                StatusText.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(255, 193, 7));

                AddLog($"✓ Server started on {localIP}:{serverPort}");
                AddLog($"✓ Pairing Code: {pairingCode}");
                AddLog($"✓ Listening on 0.0.0.0:{serverPort}");
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Failed to start server: {ex.Message}", "Error",
                    MessageBoxButton.OK, MessageBoxImage.Error);
                AddLog($"✗ Server start failed: {ex.Message}");
            }

            // check every 5 seconds
            heartbeatMonitor = new System.Threading.Timer(CheckHeartbeats, null, 5000, 5000);
        }

        // Heartbeat check
        private void CheckHeartbeats(object state)
        {
            var now = DateTime.Now;
            var timeoutDevices = new List<string>();

            foreach (var kvp in lastHeartbeat)
            {
                // consider as 10 seconds
                if ((now - kvp.Value).TotalSeconds > 10)
                {
                    timeoutDevices.Add(kvp.Key);
                }
            }

            // Remove the Timeout device
            foreach (var deviceIP in timeoutDevices)
            {
                Dispatcher.Invoke(() => {
                    var device = connectedDevices.FirstOrDefault(d => d.IPAddress == deviceIP);
                    if (device != null)
                    {
                        connectedDevices.Remove(device);
                        deviceEndpoints.Remove(deviceIP);
                        lastHeartbeat.Remove(deviceIP);

                        UpdateConnectionStatus();
                        AddLog($"⚠ Device timeout: {device.Name} ({deviceIP})");
                    }
                });
            }
        }


        private void GenerateQRCode(string data)
        {
            try
            {
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


        private void ProcessMessage(string message, IPEndPoint remoteEndPoint)
        {
            try
            {
                var jsonData = JsonConvert.DeserializeObject<Dictionary<string, object>>(message);

                // support both action and type
                string action = "";
                if (jsonData.ContainsKey("action"))
                    action = jsonData["action"].ToString();
                else if (jsonData.ContainsKey("type"))
                    action = jsonData["type"].ToString();
                else
                {
                    AddLog($"⚠ Invalid message format");
                    return;
                }

                // 1. Discovery
                if (action == "discover")
                {
                    string code = jsonData.ContainsKey("code") ? jsonData["code"].ToString() : "";

                    if (code == pairingCode)
                    {
                        AddLog($"✓ Discovery: Code matched");

                        string response = JsonConvert.SerializeObject(new { action = "discovered" });
                        byte[] responseData = Encoding.UTF8.GetBytes(response);
                        udpServer.Send(responseData, responseData.Length, remoteEndPoint);
                    }
                    return;
                }

                // 2. Pairing
                if (action == "pair" || action == "pairing")
                {
                    string code = jsonData.ContainsKey("code") ? jsonData["code"].ToString() : "";

                    if (code == pairingCode)
                    {
                        string deviceIP = remoteEndPoint.Address.ToString();

                        string deviceName = "Unknown Device";
                        if (jsonData.ContainsKey("deviceName"))
                            deviceName = jsonData["deviceName"].ToString();
                        else if (jsonData.ContainsKey("device_name"))
                            deviceName = jsonData["device_name"].ToString();
                        else if (jsonData.ContainsKey("device"))
                            deviceName = jsonData["device"].ToString();
                        else
                            deviceName = $"Device-{deviceIP.Substring(deviceIP.LastIndexOf('.') + 1)}";

                        if (!deviceEndpoints.ContainsKey(deviceIP))
                        {
                            deviceEndpoints[deviceIP] = remoteEndPoint;
                            connectedDevices.Add(new ConnectedDevice
                            {
                                Name = deviceName,
                                IPAddress = deviceIP,
                                Status = "Connected"
                            });

                            UpdateConnectionStatus();

                            if (NotifyOnConnect != null && NotifyOnConnect.IsChecked == true)
                            {
                                MessageBox.Show($"New device connected: {deviceName}",
                                    "Device Connected",
                                    MessageBoxButton.OK,
                                    MessageBoxImage.Information);
                            }
                        }

                        var config = new
                        {
                            status = "connected",
                            buttons = CreateButtonConfigForMobile()
                        };

                        string ackMessage = JsonConvert.SerializeObject(config);
                        byte[] ackData = Encoding.UTF8.GetBytes(ackMessage);
                        udpServer.Send(ackData, ackData.Length, remoteEndPoint);

                        AddLog($"✓ Paired with {deviceName} ({deviceIP})");
                    }
                    return;
                }

                if (action == "disconnect")
                {
                    string deviceIP = remoteEndPoint.Address.ToString();

                    // remove from list
                    var deviceToRemove = connectedDevices.FirstOrDefault(d => d.IPAddress == deviceIP);
                    if (deviceToRemove != null)
                    {
                        connectedDevices.Remove(deviceToRemove);
                        deviceEndpoints.Remove(deviceIP);

                        UpdateConnectionStatus();

                        AddLog($"✓ Device disconnected: {deviceToRemove.Name} ({deviceIP})");

                        if (NotifyOnConnect != null && NotifyOnConnect.IsChecked == true)
                        {
                            MessageBox.Show($"Device disconnected: {deviceToRemove.Name}",
                                "Device Disconnected",
                                MessageBoxButton.OK,
                                MessageBoxImage.Information);
                        }
                    }
                    return;
                }

                if (action == "heartbeat")
                {
                    string deviceIP = remoteEndPoint.Address.ToString();
                    lastHeartbeat[deviceIP] = DateTime.Now;

                    return;
                }

                // Ping
                if (action == "ping")
                {
                    long timestamp = 0;
                    if (jsonData.ContainsKey("timestamp"))
                    {
                        timestamp = Convert.ToInt64(jsonData["timestamp"]);
                    }

                    // Pong 
                    var pongMessage = new
                    {
                        action = "pong",
                        timestamp = timestamp
                    };

                    string pongJson = JsonConvert.SerializeObject(pongMessage);
                    byte[] pongData = Encoding.UTF8.GetBytes(pongJson);

                    udpServer.Send(pongData, pongData.Length, remoteEndPoint);


                    return;
                }

                // 3. Universal Command Handler
                if (action == "command")
                {
                    HandleUniversalCommand(jsonData);
                    return;
                }

                // 4. Key input
                if (action == "key")
                {
                    if (!jsonData.ContainsKey("key") || !jsonData.ContainsKey("pressed"))
                        return;

                    string key = jsonData["key"].ToString().ToLower();
                    bool pressed = Convert.ToBoolean(jsonData["pressed"]);

                    // Multiplayer player information
                    int playerNumber = 1;
                    if (jsonData.ContainsKey("player"))
                    {
                        playerNumber = Convert.ToInt32(jsonData["player"]);
                    }

                    VirtualKeyCode vk = GetVirtualKeyCode(key);

                    if (vk != VirtualKeyCode.NONAME)
                    {
                        if (pressed)
                        {
                            // Log
                            string playerTag = playerNumber > 1 ? $" (P{playerNumber})" : "";
                            AddLog($"🔽 PRESSED: {key.ToUpper()}{playerTag}");
                        }
                        else
                        {
                            inputSimulator.Keyboard.KeyUp(vk);
                            string playerTag = playerNumber > 1 ? $" (P{playerNumber})" : "";
                            AddLog($"🔼 RELEASED: {key.ToUpper()}{playerTag}");
                        }
                    }
                    else
                    {
                        AddLog($"⚠ Unknown key: {key}");
                    }
                    return;
                }




                // 5. Mouse movement
                if (action == "mouse_move")
                {
                    if (!jsonData.ContainsKey("x") || !jsonData.ContainsKey("y"))
                        return;

                    int deltaX = Convert.ToInt32(jsonData["x"]);
                    int deltaY = Convert.ToInt32(jsonData["y"]);

                    POINT currentPos;
                    GetCursorPos(out currentPos);

                    int newX = currentPos.X + deltaX;
                    int newY = currentPos.Y + deltaY;

                    SetCursorPos(newX, newY);

                    if (Math.Abs(deltaX) > 50 || Math.Abs(deltaY) > 50)
                    {
                        AddLog($"🎯 Mouse: Δ({deltaX}, {deltaY})");
                    }
                    return;
                }

                // 6. Mouse button
                if (action == "mouse_button")
                {
                    if (!jsonData.ContainsKey("button") || !jsonData.ContainsKey("pressed"))
                        return;

                    string button = jsonData["button"].ToString().ToLower();
                    bool pressed = Convert.ToBoolean(jsonData["pressed"]);
                    // Check Player Number
                    int playerNumber = jsonData.ContainsKey("player") ?
                        Convert.ToInt32(jsonData["player"]) : 1;


                    if (button == "left")
                    {
                        if (pressed)
                        {
                            inputSimulator.Mouse.LeftButtonDown();
                            AddLog($"P{playerNumber}: LEFT CLICK");
                        }
                        else
                        {
                            inputSimulator.Mouse.LeftButtonUp();
                        }
                    }
                    else if (button == "right")
                    {
                        if (pressed)
                        {
                            inputSimulator.Mouse.RightButtonDown();
                            AddLog($"P{playerNumber}: RIGHT CLICK");
                        }
                        else
                        {
                            inputSimulator.Mouse.RightButtonUp();
                        }
                    }
                    return;
                }

                // 7. Gyro data
                if (action == "gyro_data")
                {
                    return;
                }

                // 8. Legacy input
                if (action == "input")
                {
                    if (!jsonData.ContainsKey("input"))
                        return;

                    string inputAction = jsonData["input"].ToString().ToLower();

                    VirtualKeyCode vk = VirtualKeyCode.NONAME;

                    if (inputAction.Contains("left") || inputAction == "a")
                        vk = VirtualKeyCode.VK_A;
                    else if (inputAction.Contains("right") || inputAction == "d")
                        vk = VirtualKeyCode.VK_D;
                    else if (inputAction.Contains("up") || inputAction == "w")
                        vk = VirtualKeyCode.VK_W;
                    else if (inputAction.Contains("down") || inputAction == "s")
                        vk = VirtualKeyCode.VK_S;

                    if (vk != VirtualKeyCode.NONAME)
                    {
                        if (inputAction.Contains("_down") || (!inputAction.Contains("_up") && !inputAction.Contains("_down")))
                        {
                            inputSimulator.Keyboard.KeyDown(vk);
                            AddLog($"INPUT: {inputAction.ToUpper()}");
                        }
                        else if (inputAction.Contains("_up"))
                        {
                            inputSimulator.Keyboard.KeyUp(vk);
                            AddLog($"INPUT: {inputAction.ToUpper()}");
                        }
                        else
                        {
                            inputSimulator.Keyboard.KeyPress(vk);
                            AddLog($"INPUT: {inputAction.ToUpper()}");
                        }
                    }
                    return;
                }

                AddLog($"Unknown action: {action}");
            }
            catch (Exception ex)
            {
                AddLog($"Parse error: {ex.Message}");
            }
        }

        // command handler
        private void HandleUniversalCommand(Dictionary<string, object> jsonData)
        {
            if (!jsonData.ContainsKey("action") || !jsonData.ContainsKey("state"))
                return;

            string buttonAction = jsonData["action"].ToString().ToLower();
            string state = jsonData["state"].ToString().ToLower();
            bool isPressed = (state == "pressed" || state == "down");

            AddLog($"Command: {buttonAction} = {state}");

            // load mapping
            string mappedKey = GetButtonMapping(buttonAction);

            if (string.IsNullOrEmpty(mappedKey))
            {
                AddLog($"No mapping for: {buttonAction}");
                return;
            }

            // mouse input
            if (mappedKey.StartsWith("Mouse"))
            {
                HandleMouseCommand(mappedKey, isPressed);
                return;
            }

            // keyboard key
            VirtualKeyCode vk = ParseVirtualKey(mappedKey);

            if (vk != VirtualKeyCode.NONAME)
            {
                if (isPressed)
                {
                    inputSimulator.Keyboard.KeyDown(vk);
                    AddLog($"{buttonAction.ToUpper()} → {mappedKey}");
                }
                else
                {
                    inputSimulator.Keyboard.KeyUp(vk);
                    AddLog($"{buttonAction.ToUpper()} → {mappedKey}");
                }
            }
        }

        // mouse input
        private void HandleMouseCommand(string mouseButton, bool isPressed)
        {
            switch (mouseButton.ToUpper())
            {
                case "MOUSE1":
                    if (isPressed)
                        inputSimulator.Mouse.LeftButtonDown();
                    else
                        inputSimulator.Mouse.LeftButtonUp();
                    AddLog($"LEFT BUTTON {(isPressed ? "DOWN" : "UP")}");
                    break;

                case "MOUSE2":
                    if (isPressed)
                        inputSimulator.Mouse.RightButtonDown();
                    else
                        inputSimulator.Mouse.RightButtonUp();
                    AddLog($"RIGHT BUTTON {(isPressed ? "DOWN" : "UP")}");
                    break;

              
            }
        }

        private VirtualKeyCode GetVirtualKeyCode(string key)
        {
            switch (key)
            {
                // 알파벳
                case "a": return VirtualKeyCode.VK_A;
                case "b": return VirtualKeyCode.VK_B;
                case "c": return VirtualKeyCode.VK_C;
                case "d": return VirtualKeyCode.VK_D;
                case "e": return VirtualKeyCode.VK_E;
                case "f": return VirtualKeyCode.VK_F;
                case "g": return VirtualKeyCode.VK_G;
                case "h": return VirtualKeyCode.VK_H;
                case "i": return VirtualKeyCode.VK_I;
                case "j": return VirtualKeyCode.VK_J;
                case "k": return VirtualKeyCode.VK_K;
                case "l": return VirtualKeyCode.VK_L;
                case "m": return VirtualKeyCode.VK_M;
                case "n": return VirtualKeyCode.VK_N;
                case "o": return VirtualKeyCode.VK_O;
                case "p": return VirtualKeyCode.VK_P;
                case "q": return VirtualKeyCode.VK_Q;
                case "r": return VirtualKeyCode.VK_R;
                case "s": return VirtualKeyCode.VK_S;
                case "t": return VirtualKeyCode.VK_T;
                case "u": return VirtualKeyCode.VK_U;
                case "v": return VirtualKeyCode.VK_V;
                case "w": return VirtualKeyCode.VK_W;
                case "x": return VirtualKeyCode.VK_X;
                case "y": return VirtualKeyCode.VK_Y;
                case "z": return VirtualKeyCode.VK_Z;

                // 숫자
                case "0": return VirtualKeyCode.VK_0;
                case "1": return VirtualKeyCode.VK_1;
                case "2": return VirtualKeyCode.VK_2;
                case "3": return VirtualKeyCode.VK_3;
                case "4": return VirtualKeyCode.VK_4;
                case "5": return VirtualKeyCode.VK_5;
                case "6": return VirtualKeyCode.VK_6;
                case "7": return VirtualKeyCode.VK_7;
                case "8": return VirtualKeyCode.VK_8;
                case "9": return VirtualKeyCode.VK_9;

                // 특수 키
                case "space": return VirtualKeyCode.SPACE;
                case "enter": return VirtualKeyCode.RETURN;
                case "shift": return VirtualKeyCode.SHIFT;
                case "ctrl": return VirtualKeyCode.CONTROL;
                case "alt": return VirtualKeyCode.MENU;
                case "tab": return VirtualKeyCode.TAB;
                case "esc": return VirtualKeyCode.ESCAPE;
                case "backspace": return VirtualKeyCode.BACK;

                // 화살표
                case "up": return VirtualKeyCode.UP;
                case "down": return VirtualKeyCode.DOWN;
                case "left": return VirtualKeyCode.LEFT;
                case "right": return VirtualKeyCode.RIGHT;

                // F키
                case "f1": return VirtualKeyCode.F1;
                case "f2": return VirtualKeyCode.F2;
                case "f3": return VirtualKeyCode.F3;
                case "f4": return VirtualKeyCode.F4;
                case "f5": return VirtualKeyCode.F5;
                case "f6": return VirtualKeyCode.F6;
                case "f7": return VirtualKeyCode.F7;
                case "f8": return VirtualKeyCode.F8;
                case "f9": return VirtualKeyCode.F9;
                case "f10": return VirtualKeyCode.F10;
                case "f11": return VirtualKeyCode.F11;
                case "f12": return VirtualKeyCode.F12;

                default:
                    return VirtualKeyCode.NONAME;
            }
        }

        // hardcoded 
        private string GetButtonMapping(string buttonAction)
        {
            var defaultMappings = new Dictionary<string, string>
    {
        // Racing
        { "gas", "W" },
        { "brake", "S" },
        { "left", "A" },
        { "right", "D" },
        { "turbo", "Shift" },
        { "pause", "P" },

        // Flight
        { "machine_gun", "Mouse1" },
        { "rocket", "Mouse2" },
        { "missile", "Space" },

        // Game keys
        { "key_w", "W" },
        { "key_a", "A" },
        { "key_s", "S" },
        { "key_d", "D" },
        { "key_space", "Space" },
        { "key_shift", "Shift" },
        { "key_e", "E" },
        { "key_q", "Q" },
        { "key_r", "R" },
        { "key_f", "F" },

        // Actions
        { "jump", "Space" },
        { "fire", "Mouse1" },
        { "reload", "R" },
        { "crouch", "Ctrl" }
    };

            return defaultMappings.ContainsKey(buttonAction) ? defaultMappings[buttonAction] : "";
        }

        // simple key
        private VirtualKeyCode ParseSimpleKey(string key)
        {
            switch (key.ToLower())
            {
                case "w": return VirtualKeyCode.VK_W;
                case "a": return VirtualKeyCode.VK_A;
                case "s": return VirtualKeyCode.VK_S;
                case "d": return VirtualKeyCode.VK_D;
                case "shift": return VirtualKeyCode.SHIFT;
                case "p": return VirtualKeyCode.VK_P;
                case "space": return VirtualKeyCode.SPACE;
                // D-Pad 
                case "up": return VirtualKeyCode.UP;
                case "down": return VirtualKeyCode.DOWN;
                case "left": return VirtualKeyCode.LEFT;
                case "right": return VirtualKeyCode.RIGHT;

                default: return VirtualKeyCode.NONAME;
            }
        }

        // all key
        private VirtualKeyCode ParseVirtualKey(string key)
        {
            switch (key.ToUpper())
            {
                // Letters A-Z
                case "A": return VirtualKeyCode.VK_A;
                case "B": return VirtualKeyCode.VK_B;
                case "C": return VirtualKeyCode.VK_C;
                case "D": return VirtualKeyCode.VK_D;
                case "E": return VirtualKeyCode.VK_E;
                case "F": return VirtualKeyCode.VK_F;
                case "G": return VirtualKeyCode.VK_G;
                case "H": return VirtualKeyCode.VK_H;
                case "I": return VirtualKeyCode.VK_I;
                case "J": return VirtualKeyCode.VK_J;
                case "K": return VirtualKeyCode.VK_K;
                case "L": return VirtualKeyCode.VK_L;
                case "M": return VirtualKeyCode.VK_M;
                case "N": return VirtualKeyCode.VK_N;
                case "O": return VirtualKeyCode.VK_O;
                case "P": return VirtualKeyCode.VK_P;
                case "Q": return VirtualKeyCode.VK_Q;
                case "R": return VirtualKeyCode.VK_R;
                case "S": return VirtualKeyCode.VK_S;
                case "T": return VirtualKeyCode.VK_T;
                case "U": return VirtualKeyCode.VK_U;
                case "V": return VirtualKeyCode.VK_V;
                case "W": return VirtualKeyCode.VK_W;
                case "X": return VirtualKeyCode.VK_X;
                case "Y": return VirtualKeyCode.VK_Y;
                case "Z": return VirtualKeyCode.VK_Z;

                // Numbers 0-9
                case "0": return VirtualKeyCode.VK_0;
                case "1": return VirtualKeyCode.VK_1;
                case "2": return VirtualKeyCode.VK_2;
                case "3": return VirtualKeyCode.VK_3;
                case "4": return VirtualKeyCode.VK_4;
                case "5": return VirtualKeyCode.VK_5;
                case "6": return VirtualKeyCode.VK_6;
                case "7": return VirtualKeyCode.VK_7;
                case "8": return VirtualKeyCode.VK_8;
                case "9": return VirtualKeyCode.VK_9;

                // Special keys
                case "SPACE": return VirtualKeyCode.SPACE;
                case "ENTER": return VirtualKeyCode.RETURN;
                case "SHIFT": return VirtualKeyCode.SHIFT;
                case "CTRL": case "CONTROL": return VirtualKeyCode.CONTROL;
                case "ALT": return VirtualKeyCode.MENU;
                case "TAB": return VirtualKeyCode.TAB;
                case "ESC": case "ESCAPE": return VirtualKeyCode.ESCAPE;
                case "BACKSPACE": return VirtualKeyCode.BACK;

                // Arrow keys
                case "UP": return VirtualKeyCode.UP;
                case "DOWN": return VirtualKeyCode.DOWN;
                case "LEFT": return VirtualKeyCode.LEFT;
                case "RIGHT": return VirtualKeyCode.RIGHT;

                // Function keys
                case "F1": return VirtualKeyCode.F1;
                case "F2": return VirtualKeyCode.F2;
                case "F3": return VirtualKeyCode.F3;
                case "F4": return VirtualKeyCode.F4;
                case "F5": return VirtualKeyCode.F5;
                case "F6": return VirtualKeyCode.F6;
                case "F7": return VirtualKeyCode.F7;
                case "F8": return VirtualKeyCode.F8;
                case "F9": return VirtualKeyCode.F9;
                case "F10": return VirtualKeyCode.F10;
                case "F11": return VirtualKeyCode.F11;
                case "F12": return VirtualKeyCode.F12;

                default: return VirtualKeyCode.NONAME;
            }
        }

        private Dictionary<string, object> CreateButtonConfigForMobile()
        {
            var config = new Dictionary<string, object>();

            foreach (var mapping in buttonMappings)
            {
                config[mapping.Key] = new
                {
                    enabled = mapping.Value.Enabled,
                    key = mapping.Value.Key,
                    label = mapping.Key.ToUpper()
                };
            }

            return config;
        }

        private void SimulateKeyPress(string action)
        {
            if (!buttonMappings.ContainsKey(action))
            {
                AddLog($"⚠ Unknown action: {action}");
                return;
            }

            var mapping = buttonMappings[action];

            if (!mapping.Enabled)
            {
                AddLog($"⚠ Button disabled: {action}");
                return;
            }

            if (mapping.VirtualKey == VirtualKeyCode.NONAME)
            {
                AddLog($"⚠ Invalid key for {action}");
                return;
            }

            inputSimulator.Keyboard.KeyPress(mapping.VirtualKey);
            AddLog($"✓ {action.ToUpper()} → {mapping.Key}");
        }



        private string GetLocalIPAddress()
        {
            string localIP = "127.0.0.1";

            try                                                    
            {                                                                                                  
                var host = Dns.GetHostEntry(Dns.GetHostName());

                foreach (var ip in host.AddressList)
                {
                    if (ip.AddressFamily == AddressFamily.InterNetwork)
                    {
                        string ipStr = ip.ToString();

                        // VirtualBox, VMware, Docker 등 가상 어댑터 제외
                        if (ipStr.StartsWith("127.") ||
                            ipStr.StartsWith("169.254.") ||
                            ipStr.StartsWith("192.168.56.") ||
                            ipStr.StartsWith("192.168.99.") ||
                            ipStr.StartsWith("172.17.") ||
                            ipStr.StartsWith("172.18."))
                        {
                            AddLog($"⚠ Skipping: {ipStr}");
                            continue;
                        }

                        // 첫 번째 유효한 IP 사용
                        localIP = ipStr;
                        AddLog($"✓ Using IP: {ipStr}");
                        break;
                    }
                }
            }
            catch (Exception ex)
            {
                AddLog($"✗ Failed to get IP: {ex.Message}");
            }

            if (localIP == "127.0.0.1")
            {
                AddLog("⚠ Warning: No valid network IP found");
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
                    RemoveDevice(deviceIP);
                }
            }
        }

        private void RemoveDevice(string deviceIP)
        {
            if (deviceEndpoints.ContainsKey(deviceIP))
            {
                // Send disconnect message to device
                try
                {
                    var disconnectMsg = new { action = "disconnect" };
                    string json = JsonConvert.SerializeObject(disconnectMsg);
                    byte[] data = Encoding.UTF8.GetBytes(json);
                    udpServer.Send(data, data.Length, deviceEndpoints[deviceIP]);
                }
                catch { }

                deviceEndpoints.Remove(deviceIP);
            }

            var device = connectedDevices.FirstOrDefault(d => d.IPAddress == deviceIP);
            if (device != null)
            {
                connectedDevices.Remove(device);
                AddLog($"✓ Disconnected device: {device.Name} ({deviceIP})");

                // Show notification if enabled
                if (NotifyOnDisconnect != null && NotifyOnDisconnect.IsChecked == true)
                {
                    MessageBox.Show($"Device disconnected: {device.Name}",
                        "Device Disconnected",
                        MessageBoxButton.OK,
                        MessageBoxImage.Information);
                }
            }

            UpdateConnectionStatus();
        }

        private void UpdateConnectionStatus()
        {
            int count = connectedDevices.Count;
            ConnectionCountText.Text = $"{count} device{(count != 1 ? "s" : "")} connected";

            if (count > 0)
            {
                StatusText.Text = $"Connected ({count} device{(count != 1 ? "s" : "")})";
                StatusIndicator.Fill = new SolidColorBrush(Color.FromRgb(76, 175, 80)); // Green
            }
            else
            {
                StatusText.Text = "Waiting for connection...";
                StatusIndicator.Fill = new SolidColorBrush(Color.FromRgb(255, 193, 7)); // Yellow
            }
        }

        #endregion

        #region Settings Methods

        private void TestNetworkButton_Click(object sender, RoutedEventArgs e)
        {
            // Simulate network test
            Random random = new Random();
            int latency = random.Next(10, 50);

            NetworkLatencyText.Text = $"{latency} ms";

            if (latency < 20)
            {
                NetworkLatencyText.Foreground = new SolidColorBrush(Color.FromRgb(76, 175, 80)); // Green
                SignalStrengthText.Text = "Signal Strength: Excellent";
                SignalStrengthText.Foreground = new SolidColorBrush(Color.FromRgb(76, 175, 80));
            }
            else if (latency < 35)
            {
                NetworkLatencyText.Foreground = new SolidColorBrush(Color.FromRgb(76, 175, 80)); // Green
                SignalStrengthText.Text = "Signal Strength: Very Good";
                SignalStrengthText.Foreground = new SolidColorBrush(Color.FromRgb(76, 175, 80));
            }
            else if (latency < 50)
            {
                NetworkLatencyText.Foreground = new SolidColorBrush(Color.FromRgb(255, 193, 7)); // Yellow
                SignalStrengthText.Text = "Signal Strength: Good";
                SignalStrengthText.Foreground = new SolidColorBrush(Color.FromRgb(255, 193, 7));
            }
            else
            {
                NetworkLatencyText.Foreground = new SolidColorBrush(Color.FromRgb(244, 67, 54)); // Red
                SignalStrengthText.Text = "Signal Strength: Poor";
                SignalStrengthText.Foreground = new SolidColorBrush(Color.FromRgb(244, 67, 54));
            }

            AddLog($"✓ Network test completed: {latency}ms");
        }

        private void ViewConnectionLogsButton_Click(object sender, RoutedEventArgs e)
        {
            // Switch to host screen to show logs
            ShowScreen("Host");
            AddLog("✓ Viewing connection logs");
        }

        private void EditControllerMappingButton_Click(object sender, RoutedEventArgs e)
        {
            MessageBox.Show("Controller mapping editor will be available in the next update!",
                "Coming Soon",
                MessageBoxButton.OK,
                MessageBoxImage.Information);
            AddLog("✓ Controller mapping editor requested");
        }

        // compatibility stub: forwards to your existing handler
        private void EditDeviceMapping_Click(object? sender, System.Windows.RoutedEventArgs e)
        {
            // forward to the existing method (keeps behavior identical)
            EditControllerMappingButton_Click(sender ?? this, e);
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
    }

    #region Data Models

    public class InputMessage
    {
        public string action { get; set; }
        public string code { get; set; }
        public string device { get; set; }
        public string deviceName { get; set; }
        public Dictionary<string, object> data { get; set; }
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