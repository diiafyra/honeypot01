import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../provider/ai_key_provider.dart';
import '../provider/label_provider.dart';
import '../widgets/ai_config/ai_key_list.dart';
import '../widgets/ai_config/label_list.dart';
import '../widgets/ai_config/add_key_dialog.dart';
import '../widgets/ai_config/add_label_dialog.dart';

enum AIConfigTab { labels, keys }

class AIConfigScreen extends StatefulWidget {
  final GlobalKey<NavigatorState> navKey;

  const AIConfigScreen({
    super.key,
    required this.navKey,
  });

  @override
  State<AIConfigScreen> createState() => _AIConfigScreenState();
}

class _AIConfigScreenState extends State<AIConfigScreen> {
  AIConfigTab _currentTab = AIConfigTab.labels;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  /// Load data for both providers
  Future<void> _loadData() async {
    // Use Future.microtask to avoid calling during build
    Future.microtask(() {
      if (!mounted) return;

      context.read<AIKeyProvider>().loadKeys();
      context.read<LabelProvider>().loadLabels();
    });
  }

  /// Show appropriate dialog based on current tab
  void _showAddDialog() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) {
        return _currentTab == AIConfigTab.labels
            ? const AddLabelDialog()
            : const AddKeyDialog();
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: _buildAppBar(),
      body: Column(
        children: [
          _buildTabSelector(),
          const Divider(height: 1),
          Expanded(
            child: _buildTabContent(),
          ),
        ],
      ),
      floatingActionButton: _buildFloatingActionButton(),
    );
  }

  // ==================== APP BAR ====================

  PreferredSizeWidget _buildAppBar() {
    return AppBar(
      elevation: 0,
      backgroundColor: Colors.white,
      foregroundColor: Colors.black,
      title: const Text(
        'AI CONFIG',
        style: TextStyle(
          fontWeight: FontWeight.bold,
          letterSpacing: 0.5,
        ),
      ),
      centerTitle: false,
    );
  }

  // ==================== TAB SELECTOR ====================

  Widget _buildTabSelector() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        children: [
          _buildTabButton(
            label: 'Danh sách phân loại',
            tab: AIConfigTab.labels,
            icon: Icons.label,
          ),
          Container(
            height: 20,
            width: 1,
            margin: const EdgeInsets.symmetric(horizontal: 12),
            color: Colors.grey[300],
          ),
          _buildTabButton(
            label: 'API Keys',
            tab: AIConfigTab.keys,
            icon: Icons.key,
          ),
        ],
      ),
    );
  }

  Widget _buildTabButton({
    required String label,
    required AIConfigTab tab,
    required IconData icon,
  }) {
    final isActive = _currentTab == tab;

    return GestureDetector(
      onTap: () => setState(() => _currentTab = tab),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: isActive ? Colors.blue[50] : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            Icon(
              icon,
              size: 18,
              color: isActive ? Colors.blue[700] : Colors.grey[600],
            ),
            const SizedBox(width: 6),
            Text(
              label,
              style: TextStyle(
                fontWeight: isActive ? FontWeight.bold : FontWeight.normal,
                color: isActive ? Colors.blue[700] : Colors.grey[700],
                fontSize: 14,
              ),
            ),
          ],
        ),
      ),
    );
  }


  Widget _buildTabContent() {
    return _currentTab == AIConfigTab.labels
        ? const LabelList()
        : const AIKeyList();
  }


  Widget _buildFloatingActionButton() {
    return FloatingActionButton(
      backgroundColor: Colors.blue[600],
      foregroundColor: Colors.white,
      onPressed: _showAddDialog,
      child: const Icon(Icons.add),
    );
  }

}