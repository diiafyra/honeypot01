import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
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

class _HomeScreenState extends State<HomeScreen> {
  static const platform = MethodChannel('cmc.cs.honeypot01/permissions');

  // Permission status map
  Map<String, bool> permissionStatus = {
    'phone': false,
    'allFiles': false,
    'callScreening': false,
  };

  int totalNumbers = 0;
  int totalCalls = 0;

  final List<_PermissionItem> allPermissions = [
    _PermissionItem(
      id: 'phone',
      title: 'Phone Permissions',
      icon: Icons.call_outlined,
      description: 'Read phone state, answer calls, call logs',
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
    _checkAllPermissions();
    _fetchStats();
  }

  Future<void> _checkAllPermissions() async {
    // Check phone permissions (READ_PHONE_STATE, ANSWER_PHONE_CALLS, READ_CALL_LOG)
    final phoneGranted = await Permission.phone.isGranted;

    // Check all files access (MANAGE_EXTERNAL_STORAGE)
    final allFilesGranted = await Permission.manageExternalStorage.isGranted;

    // Check call screening role via method channel
    bool callScreeningGranted = false;
    try {
      callScreeningGranted =
          await platform.invokeMethod('hasCallScreeningRole') ?? false;
    } catch (e) {
      debugPrint('Error checking call screening role: $e');
    }

    if (mounted) {
      setState(() {
        permissionStatus = {
          'phone': phoneGranted,
          'allFiles': allFilesGranted,
          'callScreening': callScreeningGranted,
        };
      });
    }
  }

  Future<void> _fetchStats() async {
    try {
      final snapshot = await FirebaseFirestore.instance
          .collection('spam_numbers')
          .get();

      int totalCallsSum = 0;
      for (final doc in snapshot.docs) {
        final data = doc.data();
        final callCount = data['call_count'] as int? ?? 0;
        totalCallsSum += callCount;
      }

      if (mounted) {
        setState(() {
          totalNumbers = snapshot.docs.length;
          totalCalls = totalCallsSum;
        });
      }
    } catch (e) {
      debugPrint('Error fetching stats: $e');
    }
  }

  Future<void> _requestPermission(String permissionId) async {
    switch (permissionId) {
      case 'phone':
        await Permission.phone.request();
        break;
      case 'allFiles':
        await Permission.manageExternalStorage.request();
        break;
      case 'callScreening':
        try {
          await platform.invokeMethod('requestCallScreeningRole');
        } catch (e) {
          debugPrint('Error requesting call screening role: $e');
        }
        break;
    }
    // Recheck all permissions after request
    await _checkAllPermissions();
  }

  bool get _allPermissionsGranted =>
      permissionStatus.values.every((granted) => granted);

  List<_PermissionItem> get _missingPermissions {
    return allPermissions
        .where((p) => !(permissionStatus[p.id] ?? false))
        .toList();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F8FB),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 16.0),
                child: Text(
                  'TỔNG QUAN',
                  style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.bold,
                    color: Colors.black87,
                  ),
                ),
              ),
              // Stats Grid
              GridView.count(
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
                    value: totalNumbers.toString(),
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
              ),
              const SizedBox(height: 32),
              // Permissions Section
              _buildPermissionSection(),
            ],
          ),
        ),
      ),
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
        padding: const EdgeInsets.all(16.0),
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
                color: Colors.black87,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              label,
              style: const TextStyle(fontSize: 12, color: Colors.black54),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionSection() {
    final missingPerms = _missingPermissions;

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: _allPermissionsGranted
              ? Colors.green.shade300
              : Colors.red.shade300,
          width: 1,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  _allPermissionsGranted
                      ? Icons.check_circle_outline
                      : Icons.cancel_outlined,
                  size: 24,
                  color: _allPermissionsGranted
                      ? Colors.green
                      : Colors.red.shade400,
                ),
                const SizedBox(width: 12),
                Text(
                  _allPermissionsGranted
                      ? 'Permissions granted'
                      : 'Permission missing',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: _allPermissionsGranted
                        ? Colors.green.shade700
                        : Colors.black87,
                  ),
                ),
              ],
            ),
            if (missingPerms.isNotEmpty) ...[
              const SizedBox(height: 16),
              ...missingPerms.map((permItem) {
                return _buildPermissionItem(
                  title: permItem.title,
                  icon: permItem.icon,
                  onTap: () => _requestPermission(permItem.id),
                );
              }),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionItem({
    required String title,
    required IconData icon,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12.0),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Expanded(
              child: Row(
                children: [
                  Icon(icon, size: 20, color: Colors.black54),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      title,
                      style: const TextStyle(
                        fontSize: 14,
                        color: Colors.black87,
                        fontWeight: FontWeight.w500,
                      ),
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
