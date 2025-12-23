import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/spam_item.dart';
// add near other imports
import 'spam_detail_screen.dart';

class SpamListScreen extends StatefulWidget {
  const SpamListScreen({super.key, required this.navKey});
  final GlobalKey<NavigatorState> navKey;

  @override
  State<SpamListScreen> createState() => _SpamListScreenState();
}

class _SpamListScreenState extends State<SpamListScreen> {
  final TextEditingController _searchController = TextEditingController();
  final Color _accent = const Color(0xFFFF8C2A);
  late Stream<QuerySnapshot<Map<String, dynamic>>> _stream;
  String _query = '';

  // Filter state
  DateTime? _filterStartDate;
  DateTime? _filterEndDate;
  String _filterType = 'last_seen'; // 'last_seen' or 'create_date'

  @override
  void initState() {
    super.initState();
    // Always fetch all data, filter client-side
    _stream = FirebaseFirestore.instance
        .collection('spam_numbers')
        .orderBy('last_seen', descending: true)
        .snapshots();
    _searchController.addListener(() {
      setState(() => _query = _searchController.text.trim());
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  /// Filter items by search query
  List<SpamItem> _filterBySearch(List<SpamItem> items, String q) {
    if (q.isEmpty) return items;
    final lower = q.toLowerCase();
    return items
        .where(
          (it) =>
              it.id.contains(q) ||
              it.callerDisplayName.toLowerCase().contains(lower) ||
              it.label.toLowerCase().contains(lower),
        )
        .toList();
  }

  /// Filter items by date range (client-side)
  List<SpamItem> _filterByDateRange(List<SpamItem> items) {
    if (_filterStartDate == null && _filterEndDate == null) {
      return items;
    }

    return items.where((item) {
      // Get the date field based on filter type
      final DateTime itemDate = _filterType == 'create_date'
          ? item.createDate
          : item.lastSeen;

      // Check start date
      if (_filterStartDate != null) {
        final startOfDay = DateTime(
          _filterStartDate!.year,
          _filterStartDate!.month,
          _filterStartDate!.day,
        );
        if (itemDate.isBefore(startOfDay)) {
          return false;
        }
      }

      // Check end date
      if (_filterEndDate != null) {
        final endOfDay = DateTime(
          _filterEndDate!.year,
          _filterEndDate!.month,
          _filterEndDate!.day,
          23,
          59,
          59,
          999,
        );
        if (itemDate.isAfter(endOfDay)) {
          return false;
        }
      }

      return true;
    }).toList();
  }

  /// Apply all filters (date + search)
  List<SpamItem> _applyFilters(List<SpamItem> items) {
    var filtered = _filterByDateRange(items);
    filtered = _filterBySearch(filtered, _query);
    return filtered;
  }

  String _formatDate(DateTime d) {
    if (d.millisecondsSinceEpoch == 0) return '—';
    return DateFormat('dd/MM/yyyy').format(d);
  }

  void _showFilterDialog() {
    showDialog(
      context: context,
      builder: (BuildContext dialogContext) {
        return StatefulBuilder(
          builder: (context, setStateDialog) => AlertDialog(
            title: const Text('Lọc theo ngày'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Filter type selection
                const Text(
                  'Lọc theo:',
                  style: TextStyle(fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 8),
                RadioListTile<String>(
                  title: const Text('Lần thấy cuối'),
                  value: 'last_seen',
                  groupValue: _filterType,
                  onChanged: (value) {
                    setStateDialog(() => _filterType = value ?? 'last_seen');
                  },
                ),
                RadioListTile<String>(
                  title: const Text('Ngày tạo'),
                  value: 'create_date',
                  groupValue: _filterType,
                  onChanged: (value) {
                    setStateDialog(() => _filterType = value ?? 'last_seen');
                  },
                ),
                const SizedBox(height: 16),
                // Start date
                const Text(
                  'Ngày bắt đầu:',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 12),
                ),
                const SizedBox(height: 8),
                GestureDetector(
                  onTap: () async {
                    final picked = await showDatePicker(
                      context: context,
                      initialDate: _filterStartDate ?? DateTime.now(),
                      firstDate: DateTime(2020),
                      lastDate: DateTime.now(),
                    );
                    if (picked != null) {
                      setStateDialog(() => _filterStartDate = picked);
                    }
                  },
                  child: Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      border: Border.all(color: Colors.grey.shade300),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      _filterStartDate == null
                          ? 'Chọn ngày bắt đầu'
                          : DateFormat('dd/MM/yyyy').format(_filterStartDate!),
                      style: TextStyle(
                        color: _filterStartDate == null
                            ? Colors.black54
                            : Colors.black87,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                // End date
                const Text(
                  'Ngày kết thúc:',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 12),
                ),
                const SizedBox(height: 8),
                GestureDetector(
                  onTap: () async {
                    final picked = await showDatePicker(
                      context: context,
                      initialDate: _filterEndDate ?? DateTime.now(),
                      firstDate: DateTime(2020),
                      lastDate: DateTime.now(),
                    );
                    if (picked != null) {
                      setStateDialog(() => _filterEndDate = picked);
                    }
                  },
                  child: Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      border: Border.all(color: Colors.grey.shade300),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      _filterEndDate == null
                          ? 'Chọn ngày kết thúc'
                          : DateFormat('dd/MM/yyyy').format(_filterEndDate!),
                      style: TextStyle(
                        color: _filterEndDate == null
                            ? Colors.black54
                            : Colors.black87,
                      ),
                    ),
                  ),
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () {
                  setState(() {
                    _filterStartDate = null;
                    _filterEndDate = null;
                    _filterType = 'last_seen';
                  });
                  Navigator.pop(dialogContext);
                },
                child: const Text('Xóa'),
              ),
              ElevatedButton(
                onPressed: () {
                  setState(() {});
                  Navigator.pop(dialogContext);
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.blue,
                  foregroundColor: Colors.white,
                ),
                child: const Text('Áp dụng'),
              ),
            ],
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F8FB),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0.5,
        titleSpacing: 16,
        title: const Text(
          'Danh sách số spam',
          style: TextStyle(fontWeight: FontWeight.w700, color: Colors.black87),
        ),
      ),
      body: Column(
        children: [
          /*
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _searchController,
                    decoration: InputDecoration(
                      hintText: 'Search',
                      prefixIcon: const Icon(Icons.search),
                      suffixIcon: _query.isNotEmpty
                          ? IconButton(
                              icon: const Icon(Icons.clear),
                              onPressed: () => _searchController.clear(),
                            )
                          : null,
                      filled: true,
                      fillColor: Colors.white,
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 0,
                      ),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(14),
                        borderSide: const BorderSide(
                          color: Color(0xFFE8E8EF),
                          width: 1,
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                SizedBox(
                  height: 48,
                  width: 48,
                  child: Stack(
                    children: [
                      OutlinedButton(
                        style: OutlinedButton.styleFrom(
                          padding: EdgeInsets.zero,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(14),
                          ),
                          side: BorderSide(
                            color:
                                (_filterStartDate != null ||
                                    _filterEndDate != null)
                                ? _accent
                                : const Color(0xFFE8E8EF),
                          ),
                          backgroundColor: Colors.white,
                        ),
                        onPressed: () => _showFilterDialog(),
                        child: Icon(
                          Icons.tune,
                          size: 20,
                          color:
                              (_filterStartDate != null ||
                                  _filterEndDate != null)
                              ? _accent
                              : Colors.black87,
                        ),
                      ),
                      // Filter active indicator
                      if (_filterStartDate != null || _filterEndDate != null)
                        Positioned(
                          right: 4,
                          top: 4,
                          child: Container(
                            width: 8,
                            height: 8,
                            decoration: BoxDecoration(
                              color: _accent,
                              shape: BoxShape.circle,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          */
          Expanded(
            child: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
              stream: _stream,
              builder: (context, snapshot) {
                if (snapshot.hasError) {
                  return const Center(child: Text('Lỗi tải danh sách số spam'));
                }
                if (!snapshot.hasData) {
                  return const Center(child: CircularProgressIndicator());
                }

                final docs = snapshot.data!.docs;
                final idToDoc = {for (final d in docs) d.id: d};
                // Apply all filters (date range + search) client-side
                final items = _applyFilters(
                  docs.map((d) => SpamItem.fromDoc(d)).toList(),
                );

                if (items.isEmpty) {
                  return const Center(child: Text('Không có số spam nào'));
                }

                return ListView.builder(
                  padding: const EdgeInsets.only(bottom: 16),
                  itemCount: items.length,
                  itemBuilder: (context, i) {
                    final it = items[i];
                    return Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 6,
                      ),
                      child: InkWell(
                        borderRadius: BorderRadius.circular(12),
                        onTap: () {
                          final docSnap = idToDoc[it.id];
                          if (docSnap == null) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(
                                content: Text('Không tìm thấy tài liệu'),
                              ),
                            );
                            return;
                          }
                          widget.navKey.currentState!.push(
                            MaterialPageRoute(
                              builder: (_) =>
                                  SpamDetailScreen(doc: docSnap, item: it),
                            ),
                          );
                        },
                        child: Container(
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(
                              color: Colors.black.withValues(alpha: 0.04),
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withValues(alpha: 0.04),
                                blurRadius: 10,
                                offset: const Offset(0, 4),
                              ),
                            ],
                          ),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 12,
                              vertical: 10,
                            ),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.center,
                              children: [
                                Container(
                                  width: 52,
                                  height: 52,
                                  decoration: BoxDecoration(
                                    color: _accent,
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        it.id,
                                        style: const TextStyle(
                                          fontWeight: FontWeight.w700,
                                          fontSize: 16,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      /*
                                      Text(
                                        'Phân loại: ${it.label}',
                                        style: const TextStyle(
                                          fontSize: 13,
                                          color: Colors.black87,
                                        ),
                                      ),
                                      */
                                      // confidence removed per request
                                    ],
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Text(
                                  _formatDate(it.lastSeen),
                                  style: const TextStyle(
                                    fontSize: 12,
                                    color: Colors.black54,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
