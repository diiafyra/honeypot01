import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:cloud_firestore/cloud_firestore.dart';

class _PermissionItem {
  final String id;
  final String title;
  final IconData icon;
  final String description;

  _PermissionItem({
    required this.id,
    required this.title,
    required this.icon,
    required this.description,
  });
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.navKey});
  final GlobalKey<NavigatorState> navKey;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen>
    with WidgetsBindingObserver {
  static const platform = MethodChannel('cmc.cs.honeypot01/permissions');

  /// Permission status
  Map<String, bool> permissionStatus = {
    'phone': false,
    'allFiles': false,
    'callScreening': false,
  };

  final List<_PermissionItem> allPermissions = [
    _PermissionItem(
      id: 'phone',
      title: 'Phone Permissions',
      icon: Icons.call_outlined,
      description: 'Read phone state, answer calls, call logs, storage',
    ),
    _PermissionItem(
      id: 'allFiles',
      title: 'All Files Access',
      icon: Icons.folder_open,
      description: 'Required to read call recordings',
    ),
    _PermissionItem(
      id: 'callScreening',
      title: 'Default Caller ID & Spam App',
      icon: Icons.security,
      description: 'Required to screen incoming calls',
    ),
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkAllPermissions();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  /// Re-check permissions when app returns to foreground
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkAllPermissions();
    }
  }

  Future<void> _checkAllPermissions() async {
    try {
      final phoneGranted =
          await platform.invokeMethod<bool>('hasPhonePermissions') ?? false;
      final allFilesGranted =
          await platform.invokeMethod<bool>('hasAllFilesAccess') ?? false;
      final callScreeningGranted =
          await platform.invokeMethod<bool>('hasCallScreeningRole') ?? false;

      if (!mounted) return;

      setState(() {
        permissionStatus = {
          'phone': phoneGranted,
          'allFiles': allFilesGranted,
          'callScreening': callScreeningGranted,
        };
      });
    } catch (e) {
      debugPrint('Permission check error: $e');
    }
  }

  Future<void> _requestPermission(String id) async {
    try {
      switch (id) {
        case 'phone':
          await platform.invokeMethod('requestPhonePermissions');
          break;
        case 'allFiles':
          await platform.invokeMethod('requestAllFilesAccess');
          break;
        case 'callScreening':
          await platform.invokeMethod('requestCallScreeningRole');
          break;
      }
    } catch (e) {
      debugPrint('Request permission error ($id): $e');
    }
  }

  bool get _allPermissionsGranted =>
      permissionStatus.values.every((e) => e);

  List<_PermissionItem> get _missingPermissions =>
      allPermissions
          .where((p) => !(permissionStatus[p.id] ?? false))
          .toList();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F8FB),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 16),
              child: Text(
                'TỔNG QUAN',
                style: TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                  color: Colors.black87,
                ),
              ),
            ),

            /// 🔥 Realtime Firestore Stats
            _buildStatsGridRealtime(),

            const SizedBox(height: 32),

            /// Permissions
            _buildPermissionSection(),
          ],
        ),
      ),
    );
  }

  /// =========================
  /// FIRESTORE REALTIME STATS
  /// =========================
  Widget _buildStatsGridRealtime() {
    return StreamBuilder<QuerySnapshot>(
      stream: FirebaseFirestore.instance
          .collection('spam_numbers')
          .snapshots(),
      builder: (context, snapshot) {
        if (!snapshot.hasData) {
          return const Center(
            child: Padding(
              padding: EdgeInsets.all(32),
              child: CircularProgressIndicator(),
            ),
          );
        }

        final docs = snapshot.data!.docs;
        int totalCalls = 0;

        for (final doc in docs) {
          totalCalls += (doc['call_count'] as int? ?? 0);
        }

        return GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          crossAxisSpacing: 16,
          mainAxisSpacing: 16,
          children: [
            _buildStatCard(
              icon: Icons.shield_outlined,
              iconColor: Colors.red.shade400,
              label: 'Total number',
              value: docs.length.toString(),
            ),
            _buildStatCard(
              icon: Icons.auto_awesome_outlined,
              iconColor: Colors.blue.shade400,
              label: 'AI Accuracy',
              value: '80%',
            ),
            _buildStatCard(
              icon: Icons.call_outlined,
              iconColor: Colors.orange.shade400,
              label: 'Total calls',
              value: totalCalls.toString(),
            ),
            _buildStatCard(
              icon: Icons.flag_outlined,
              iconColor: Colors.red.shade600,
              label: 'Most Spams',
              value: 'Facebook',
            ),
          ],
        );
      },
    );
  }

  Widget _buildStatCard({
    required IconData icon,
    required Color iconColor,
    required String label,
    required String value,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.05),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 32, color: iconColor),
            const SizedBox(height: 12),
            Text(
              value,
              style: const TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              label,
              style: const TextStyle(
                fontSize: 12,
                color: Colors.black54,
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }

  /// =========================
  /// PERMISSION SECTION
  /// =========================
  Widget _buildPermissionSection() {
    final missing = _missingPermissions;

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: _allPermissionsGranted
              ? Colors.green.shade300
              : Colors.red.shade300,
        ),
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                _allPermissionsGranted
                    ? Icons.check_circle_outline
                    : Icons.cancel_outlined,
                color: _allPermissionsGranted
                    ? Colors.green
                    : Colors.red.shade400,
              ),
              const SizedBox(width: 12),
              Text(
                _allPermissionsGranted
                    ? 'Permissions granted'
                    : 'Permission missing',
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
          if (missing.isNotEmpty) ...[
            const SizedBox(height: 16),
            ...missing.map(
                  (p) => _buildPermissionItem(
                title: p.title,
                description: p.description,
                icon: p.icon,
                onTap: () => _requestPermission(p.id),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildPermissionItem({
    required String title,
    required String description,
    required IconData icon,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Row(
          children: [
            Icon(icon, size: 20, color: Colors.black54),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    description,
                    style: TextStyle(
                      fontSize: 11,
                      color: Colors.black.withValues(alpha: 0.5),
                    ),
                  ),
                ],
              ),
            ),
            Icon(
              Icons.arrow_forward_ios,
              size: 16,
              color: Colors.blue.shade400,
            ),
          ],
        ),
      ),
    );
  }
}
