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

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  static const platform = MethodChannel('cmc.cs.honeypot01/permissions');

  /// Permission status
  Map<String, bool> permissionStatus = {
    'readPhoneState': false,
    'answerPhoneCalls': false,
    'readCallLog': false,
    'storageAccess': false,
    'allFiles': false,
    'callScreening': false,
  };

  final List<_PermissionItem> allPermissions = [
    _PermissionItem(
      id: 'readPhoneState',
      title: 'Đọc trạng thái cuộc gọi',
      icon: Icons.phone_android,
      description: 'Cần thiết để theo dõi trạng thái cuộc gọi',
    ),
    _PermissionItem(
      id: 'answerPhoneCalls',
      title: 'Trả lời cuộc gọi',
      icon: Icons.call,
      description: 'Cần thiết để tự động trả lời cuộc gọi spam',
    ),
    _PermissionItem(
      id: 'readCallLog',
      title: 'Đọc nhật ký cuộc gọi',
      icon: Icons.history,
      description: 'Cần thiết để truy cập lịch sử cuộc gọi',
    ),
    _PermissionItem(
      id: 'storageAccess',
      title: 'Truy cập bộ nhớ',
      icon: Icons.storage,
      description: 'Cần thiết để lưu bản ghi cuộc gọi',
    ),
    _PermissionItem(
      id: 'allFiles',
      title: 'Truy cập tất cả tệp',
      icon: Icons.folder_open,
      description: 'Cần thiết để đọc bản ghi cuộc gọi (Android 11+)',
    ),
    _PermissionItem(
      id: 'callScreening',
      title: 'Ứng dụng ID người gọi và chống spam mặc định',
      icon: Icons.security,
      description: 'Cần thiết để sàng lọc cuộc gọi đến',
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
      final readPhoneStateGranted =
          await platform.invokeMethod<bool>('hasReadPhoneState') ?? false;
      final answerPhoneCallsGranted =
          await platform.invokeMethod<bool>('hasAnswerPhoneCalls') ?? false;
      final readCallLogGranted =
          await platform.invokeMethod<bool>('hasReadCallLog') ?? false;
      final storageAccessGranted =
          await platform.invokeMethod<bool>('hasStorageAccess') ?? false;
      final allFilesGranted =
          await platform.invokeMethod<bool>('hasAllFilesAccess') ?? false;
      final callScreeningGranted =
          await platform.invokeMethod<bool>('forceCheckCallScreening') ?? false;

      if (!mounted) return;

      setState(() {
        permissionStatus = {
          'readPhoneState': readPhoneStateGranted,
          'answerPhoneCalls': answerPhoneCallsGranted,
          'readCallLog': readCallLogGranted,
          'storageAccess': storageAccessGranted,
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
        case 'readPhoneState':
          await platform.invokeMethod('requestReadPhoneState');
          break;
        case 'answerPhoneCalls':
          await platform.invokeMethod('requestAnswerPhoneCalls');
          break;
        case 'readCallLog':
          await platform.invokeMethod('requestReadCallLog');
          break;
        case 'storageAccess':
          await platform.invokeMethod('requestStorageAccess');
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

  bool get _allPermissionsGranted => permissionStatus.values.every((e) => e);

  List<_PermissionItem> get _missingPermissions =>
      allPermissions.where((p) => !(permissionStatus[p.id] ?? false)).toList();

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
      stream: FirebaseFirestore.instance.collection('spam_numbers').snapshots(),
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
              label: 'Tổng số',
              value: docs.length.toString(),
            ),
            _buildStatCard(
              icon: Icons.auto_awesome_outlined,
              iconColor: Colors.blue.shade400,
              label: 'Độ chính xác AI',
              value: '80%',
            ),
            _buildStatCard(
              icon: Icons.call_outlined,
              iconColor: Colors.orange.shade400,
              label: 'Tổng cuộc gọi',
              value: totalCalls.toString(),
            ),
            _buildStatCard(
              icon: Icons.flag_outlined,
              iconColor: Colors.red.shade600,
              label: 'Spam nhiều nhất',
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
              style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
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
              Expanded(
                child: Text(
                  _allPermissionsGranted ? 'Đã cấp quyền' : 'Thiếu quyền',
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              IconButton(
                onPressed: _checkAllPermissions,
                icon: const Icon(Icons.refresh),
                tooltip: 'Force refresh permissions',
                color: Colors.blue.shade400,
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
