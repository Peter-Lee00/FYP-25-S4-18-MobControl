using Newtonsoft.Json;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.Linq;
using System.Windows;
using System.Windows.Input;
using WindowsInput.Native;

namespace MobControlDesktop
{
    public partial class ButtonMappingWindow : Window
    {
        private Dictionary<string, ButtonMapping> originalMappings;
        private ObservableCollection<ButtonMappingItem> mappingItems;
        private bool isTestMode = false;
        private bool isCapturingKey = false;
        private ButtonMappingItem currentCapturingItem = null;
        public bool MappingsChanged { get; private set; } = false;

        public ButtonMappingWindow(Dictionary<string, ButtonMapping> currentMappings)
        {
            InitializeComponent();

            // Clone the current mappings
            originalMappings = new Dictionary<string, ButtonMapping>();
            foreach (var mapping in currentMappings)
            {
                originalMappings[mapping.Key] = new ButtonMapping
                {
                    Enabled = mapping.Value.Enabled,
                    Key = mapping.Value.Key,
                    VirtualKey = mapping.Value.VirtualKey
                };
            }

            LoadMappings();
        }

        private void LoadMappings()
        {
            mappingItems = new ObservableCollection<ButtonMappingItem>();

            var buttonDescriptions = new Dictionary<string, string>
            {
                { "up", "D-Pad Up" },
                { "down", "D-Pad Down" },
                { "left", "D-Pad Left" },
                { "right", "D-Pad Right" },
                { "a", "Action Button A" },
                { "b", "Action Button B" },
                { "x", "Action Button X" },
                { "y", "Action Button Y" },
                { "go", "Navigation Go" },
                { "back", "Navigation Back" }
            };

            foreach (var mapping in originalMappings)
            {
                var item = new ButtonMappingItem
                {
                    ButtonKey = mapping.Key,
                    ButtonName = mapping.Key.ToUpper(),
                    Enabled = mapping.Value.Enabled,
                    SelectedKey = new KeyInfo { KeyName = mapping.Value.Key, VirtualKey = mapping.Value.VirtualKey },
                    Description = buttonDescriptions.ContainsKey(mapping.Key) ? buttonDescriptions[mapping.Key] : ""
                };

                item.PropertyChanged += MappingItem_PropertyChanged;
                mappingItems.Add(item);
            }

            MappingDataGrid.ItemsSource = mappingItems;
        }

        private void MappingItem_PropertyChanged(object sender, PropertyChangedEventArgs e)
        {
            // Mark that changes have been made
            MappingsChanged = true;
        }

        private void PressKeyButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is System.Windows.Controls.Button button)
            {
                var item = button.Tag as ButtonMappingItem;
                if (item != null)
                {
                    StartKeyCapture(item);
                }
            }
        }

        private void StartKeyCapture(ButtonMappingItem item)
        {
            isCapturingKey = true;
            currentCapturingItem = item;

            // Show overlay
            KeyCaptureOverlay.Visibility = Visibility.Visible;
            KeyCaptureButtonName.Text = $"Mapping for: {item.ButtonName}";

            // Focus window to capture keys
            this.Focus();
        }

        private void Window_KeyDown(object sender, KeyEventArgs e)
        {
            if (!isCapturingKey || currentCapturingItem == null)
                return;

            // Get the pressed key
            Key key = e.Key;

            // Handle special case for System key (Alt)
            if (key == Key.System)
            {
                key = e.SystemKey;
            }

            // Ignore modifier keys when pressed alone
            if (key == Key.LeftCtrl || key == Key.RightCtrl ||
                key == Key.LeftShift || key == Key.RightShift ||
                key == Key.LeftAlt || key == Key.RightAlt ||
                key == Key.LWin || key == Key.RWin)
            {
                return;
            }

            // Convert WPF Key to VirtualKeyCode
            VirtualKeyCode virtualKey = ConvertKeyToVirtualKeyCode(key);

            if (virtualKey != VirtualKeyCode.NONAME)
            {
                // Get friendly key name
                string keyName = GetKeyName(key, virtualKey);

                // Update the mapping
                currentCapturingItem.SelectedKey = new KeyInfo
                {
                    KeyName = keyName,
                    VirtualKey = virtualKey
                };

                MappingsChanged = true;

                // Hide overlay
                StopKeyCapture();
            }

            e.Handled = true;
        }

        private void CancelKeyCapture_Click(object sender, RoutedEventArgs e)
        {
            StopKeyCapture();
        }

        private void StopKeyCapture()
        {
            isCapturingKey = false;
            currentCapturingItem = null;
            KeyCaptureOverlay.Visibility = Visibility.Collapsed;
        }

        private string GetKeyName(Key key, VirtualKeyCode virtualKey)
        {
            // Special cases for better naming
            switch (key)
            {
                case Key.Space: return "SPACE";
                case Key.Enter: return "ENTER";
                case Key.Tab: return "TAB";
                case Key.LeftShift:
                case Key.RightShift: return "SHIFT";
                case Key.LeftCtrl:
                case Key.RightCtrl: return "CTRL";
                case Key.LeftAlt:
                case Key.RightAlt: return "ALT";
                case Key.Up: return "UP";
                case Key.Down: return "DOWN";
                case Key.Left: return "LEFT";
                case Key.Right: return "RIGHT";
                case Key.Escape: return "ESC";
                default:
                    // For letters and numbers, use uppercase
                    string keyString = key.ToString();
                    if (keyString.Length == 1)
                        return keyString.ToUpper();
                    // For D0-D9, remove the 'D' prefix
                    if (keyString.StartsWith("D") && keyString.Length == 2 && char.IsDigit(keyString[1]))
                        return keyString.Substring(1);
                    // For NumPad keys
                    if (keyString.StartsWith("NumPad"))
                        return "NUM" + keyString.Substring(6);
                    return keyString.ToUpper();
            }
        }

        private VirtualKeyCode ConvertKeyToVirtualKeyCode(Key key)
        {
            try
            {
                // Convert WPF Key to System.Windows.Forms.Keys
                int virtualKey = KeyInterop.VirtualKeyFromKey(key);
                return (VirtualKeyCode)virtualKey;
            }
            catch
            {
                return VirtualKeyCode.NONAME;
            }
        }

        private void SaveButton_Click(object sender, RoutedEventArgs e)
        {
            // Validate mappings
            if (!ValidateMappings())
            {
                return;
            }

            // Update the original mappings
            foreach (var item in mappingItems)
            {
                if (originalMappings.ContainsKey(item.ButtonKey))
                {
                    originalMappings[item.ButtonKey].Enabled = item.Enabled;
                    originalMappings[item.ButtonKey].Key = item.SelectedKey.KeyName;
                    originalMappings[item.ButtonKey].VirtualKey = item.SelectedKey.VirtualKey;
                }
            }

            // Save to file
            SaveMappingsToFile(originalMappings);

            DialogResult = true;
            Close();
        }

        private bool ValidateMappings()
        {
            // Check for duplicate key mappings (only for enabled buttons)
            var enabledKeys = mappingItems
                .Where(m => m.Enabled)
                .GroupBy(m => m.SelectedKey.KeyName)
                .Where(g => g.Count() > 1)
                .Select(g => g.Key)
                .ToList();

            if (enabledKeys.Any())
            {
                MessageBox.Show(
                    $"Duplicate key mappings detected:\n{string.Join(", ", enabledKeys)}\n\nPlease assign unique keys to each enabled button.",
                    "Validation Error",
                    MessageBoxButton.OK,
                    MessageBoxImage.Warning);
                return false;
            }

            return true;
        }

        private void SaveMappingsToFile(Dictionary<string, ButtonMapping> mappings)
        {
            try
            {
                string filePath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "button_mappings.json");
                string json = JsonConvert.SerializeObject(mappings, Formatting.Indented);
                File.WriteAllText(filePath, json);
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Failed to save mappings: {ex.Message}", "Error", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void ResetButton_Click(object sender, RoutedEventArgs e)
        {
            var result = MessageBox.Show(
                "Are you sure you want to reset all mappings to default?",
                "Reset to Default",
                MessageBoxButton.YesNo,
                MessageBoxImage.Question);

            if (result == MessageBoxResult.Yes)
            {
                ResetToDefaults();
            }
        }

        private void ResetToDefaults()
        {
            var defaults = new Dictionary<string, ButtonMapping>
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

            foreach (var item in mappingItems)
            {
                if (defaults.ContainsKey(item.ButtonKey))
                {
                    item.Enabled = defaults[item.ButtonKey].Enabled;
                    item.SelectedKey = new KeyInfo
                    {
                        KeyName = defaults[item.ButtonKey].Key,
                        VirtualKey = defaults[item.ButtonKey].VirtualKey
                    };
                }
            }

            MappingsChanged = true;
        }

        private void CancelButton_Click(object sender, RoutedEventArgs e)
        {
            if (MappingsChanged)
            {
                var result = MessageBox.Show(
                    "You have unsaved changes. Are you sure you want to cancel?",
                    "Unsaved Changes",
                    MessageBoxButton.YesNo,
                    MessageBoxImage.Question);

                if (result == MessageBoxResult.No)
                {
                    return;
                }
            }

            DialogResult = false;
            Close();
        }

        private void TestModeCheckBox_Checked(object sender, RoutedEventArgs e)
        {
            isTestMode = true;
            TestModeIndicator.Visibility = Visibility.Visible;
            MessageBox.Show(
                "Test Mode Enabled!\n\nConnect your mobile controller and press buttons to see which keys are triggered.\n\nCheck the main window's log for real-time feedback.",
                "Test Mode",
                MessageBoxButton.OK,
                MessageBoxImage.Information);
        }

        private void TestModeCheckBox_Unchecked(object sender, RoutedEventArgs e)
        {
            isTestMode = false;
            TestModeIndicator.Visibility = Visibility.Collapsed;
        }

        public Dictionary<string, ButtonMapping> GetUpdatedMappings()
        {
            return originalMappings;
        }
    }

    // Data model for DataGrid binding
    public class ButtonMappingItem : INotifyPropertyChanged
    {
        private bool _enabled;
        private KeyInfo _selectedKey;

        public string ButtonKey { get; set; }
        public string ButtonName { get; set; }
        public string Description { get; set; }

        public bool Enabled
        {
            get => _enabled;
            set
            {
                if (_enabled != value)
                {
                    _enabled = value;
                    OnPropertyChanged(nameof(Enabled));
                }
            }
        }

        public KeyInfo SelectedKey
        {
            get => _selectedKey;
            set
            {
                if (_selectedKey != value)
                {
                    _selectedKey = value;
                    OnPropertyChanged(nameof(SelectedKey));
                }
            }
        }

        public event PropertyChangedEventHandler PropertyChanged;

        protected void OnPropertyChanged(string propertyName)
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        }
    }

    // Key info for storing key data
    public class KeyInfo
    {
        public string KeyName { get; set; }
        public VirtualKeyCode VirtualKey { get; set; }

        public override string ToString()
        {
            return KeyName;
        }

        public override bool Equals(object obj)
        {
            if (obj is KeyInfo other)
            {
                return KeyName == other.KeyName && VirtualKey == other.VirtualKey;
            }
            return false;
        }

        public override int GetHashCode()
        {
            return KeyName.GetHashCode() ^ VirtualKey.GetHashCode();
        }
    }
}