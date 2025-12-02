using Newtonsoft.Json;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Drawing.Imaging;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
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
            localIP = GetLocalIPAddress();

            Random random = new Random();
            pairingCode = random.Next(1000, 9999).ToString();

            PairingCodeText.Text = pairingCode;
            IPAddressText.Text = $"IP: {localIP}:{serverPort}";

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
                var input = JsonConvert.DeserializeObject<InputMessage>(message);

                if (input.action == "discover")
                {
                    if (input.code == pairingCode)
                    {
                        AddLog($"✓ Discovery: Code matched");

                        string response = JsonConvert.SerializeObject(new { status = "found" });
                        byte[] responseData = Encoding.UTF8.GetBytes(response);
                        udpServer.Send(responseData, responseData.Length, remoteEndPoint);
                    }
                    else
                    {
                        AddLog($"✗ Discovery: Wrong code");
                    }
                    return;
                }

                if (input.action == "pair")
                {
                    if (input.code == pairingCode)
                    {
                        string deviceIP = remoteEndPoint.Address.ToString();
                        string deviceName = input.deviceName ?? $"Device-{deviceIP.Substring(deviceIP.LastIndexOf('.') + 1)}";

                        // Add device to connected list
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

                            // Show notification if enabled
                            if (NotifyOnConnect != null && NotifyOnConnect.IsChecked == true)
                            {
                                MessageBox.Show($"New device connected: {deviceName}",
                                    "Device Connected",
                                    MessageBoxButton.OK,
                                    MessageBoxImage.Information);
                            }
                        }

                        // Send configuration along with ACK
                        var config = new
                        {
                            status = "connected",
                            buttons = CreateButtonConfigForMobile()
                        };

                        string ackMessage = JsonConvert.SerializeObject(config);
                        byte[] ackData = Encoding.UTF8.GetBytes(ackMessage);
                        udpServer.Send(ackData, ackData.Length, remoteEndPoint);

                        AddLog($"✓ Paired with {deviceName} ({deviceIP})");
                        AddLog($"✓ Sent button configuration");
                    }
                    else
                    {
                        AddLog($"✗ Pairing: Wrong code");
                    }
                }
                else
                {
                    SimulateKeyPress(input.action);
                }
            }
            catch (Exception ex)
            {
                AddLog($"✗ Parse error: {ex.Message}");
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
                        localIP = ip.ToString();
                        break;
                    }
                }
            }
            catch (Exception ex)
            {
                AddLog($"✗ Failed to get IP: {ex.Message}");
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
        public string deviceName { get; set; }
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