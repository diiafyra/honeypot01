import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'dart:io';
import '../models/spam_item.dart';
import '../widgets/audio_player_bar.dart';
import 'package:path/path.dart' as p;

Future<String?> _resolveLocalAudioPath(String rawPath) async {
  if (rawPath.trim().isEmpty) return null;
  var pathStr = rawPath.trim();
  if (pathStr.startsWith('file://')) {
    pathStr = pathStr.replaceFirst('file://', '');
  }

  try {
    // If it's an existing file, return it
    final entType = await FileSystemEntity.type(pathStr, followLinks: false);
    if (entType == FileSystemEntityType.file) {
      final f = File(pathStr);
      if (await f.exists()) return pathStr;
    }

    // If it's a directory, list files and pick the newest matching audio extension
    final dir = Directory(pathStr);
    if (await dir.exists()) {
      final exts = ['.mp3', '.m4a', '.wav', '.ogg', '.3gp', '.aac', '.flac'];
      final files = <File>[];
      await for (final e in dir.list(recursive: false, followLinks: false)) {
        if (e is File) {
          final ext = p.extension(e.path).toLowerCase();
          if (exts.contains(ext)) files.add(e);
        }
      }
      if (files.isNotEmpty) {
        files.sort(
          (a, b) => b.statSync().modified.compareTo(a.statSync().modified),
        );
        return files.first.path;
      }
    }

    // Try parent directory match for base filename (e.g., path without ext)
    final parent = Directory(p.dirname(pathStr));
    if (await parent.exists()) {
      final base = p.basenameWithoutExtension(pathStr).toLowerCase();
      if (base.isNotEmpty) {
        final exts = ['.mp3', '.m4a', '.wav', '.ogg', '.3gp', '.aac', '.flac'];
        final candidates = <File>[];
        await for (final ent in parent.list(
          recursive: false,
          followLinks: false,
        )) {
          if (ent is File) {
            final name = p.basenameWithoutExtension(ent.path).toLowerCase();
            final ext = p.extension(ent.path).toLowerCase();
            if (name == base && exts.contains(ext)) candidates.add(ent);
          }
        }
        if (candidates.isNotEmpty) {
          candidates.sort(
            (a, b) => b.statSync().modified.compareTo(a.statSync().modified),
          );
          return candidates.first.path;
        }
      }
    }
  } catch (e) {
    debugPrint('resolveLocalAudioPath error for "$rawPath": $e');
    return null;
  }
  return null;
}

class SpamDetailScreen extends StatelessWidget {
  final DocumentSnapshot<Map<String, dynamic>> doc;
  final SpamItem item;
  const SpamDetailScreen({super.key, required this.doc, required this.item});

  String _formatDate(DateTime d) {
    if (d.millisecondsSinceEpoch == 0) return '—';
    return DateFormat('dd MMM yyyy').format(d);
  }

  @override
  Widget build(BuildContext context) {
    final data = doc.data() ?? {};
    final verification =
        data['verification_status'] ?? item.verificationStatus ?? 'UNKNOWN';
    final callerName =
        data['caller_display_name'] ?? item.callerDisplayName ?? '';
    final callType =
        data['handle_presentation'] ?? item.handlePresentation ?? 'UNKNOWN';
    final label = data['label'] ?? item.label ?? '';
    final callLogsStream = FirebaseFirestore.instance
        .collection('tool_call_logs')
        .where('spam_number', isEqualTo: item.id)
        .snapshots();

    return Scaffold(
      appBar: AppBar(
        title: const Text('CHI TIẾT'),
        backgroundColor: Colors.white,
        foregroundColor: Colors.black87,
        elevation: 0.5,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 1st block: header with number and last call
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.indigo[700],
                borderRadius: BorderRadius.circular(14),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.id,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Phân loại: ${label.toString()}',
                    style: const TextStyle(color: Colors.white70, fontSize: 12),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Last call: ${_formatDate(item.lastSeen)}',
                    style: const TextStyle(color: Colors.white70, fontSize: 12),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 16),

            // 2nd block: Thông tin khác
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.grey.shade100,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'THÔNG TIN KHÁC',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: Colors.black87,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 20),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Flexible(
                        flex: 3,
                        child: Text(
                          'Verification status:',
                          style: TextStyle(
                            fontWeight: FontWeight.w600,
                            fontSize: 12,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Flexible(flex: 2, child: Text(verification.toString())),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Flexible(
                        flex: 3,
                        child: Text(
                          'Handle Presentation:',
                          style: TextStyle(
                            fontWeight: FontWeight.w600,
                            fontSize: 12,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Flexible(flex: 2, child: Text(callType.toString())),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Flexible(
                        flex: 3,
                        child: Text(
                          'Caller display name:',
                          style: TextStyle(
                            fontWeight: FontWeight.w600,
                            fontSize: 12,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Flexible(flex: 2, child: Text(callerName.toString())),
                    ],
                  ),
                ],
              ),
            ),

            const SizedBox(height: 16),

            // 3rd block: Call logs (blank for now)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.purple.shade50,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'CALL LOGS',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: Colors.black87,
                    ),
                  ),
                  const SizedBox(height: 12),
                  StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
                    stream: callLogsStream,
                    builder: (context, snapshot) {
                      if (snapshot.hasError) {
                        return const Text('Error loading call logs');
                      }
                      if (!snapshot.hasData) {
                        return const CircularProgressIndicator();
                      }
                      final logs = snapshot.data!.docs;
                      if (logs.isEmpty) {
                        return const Text('No call logs available');
                      }
                      // Sort logs from newest to oldest (descending)
                      final sortedLogs = List.from(logs);
                      sortedLogs.sort((a, b) {
                        DateTime? timeA, timeB;
                        final rawTsA =
                            a['call_time'] ??
                            a['timestamp'] ??
                            a['time'] ??
                            a['created_at'];
                        final rawTsB =
                            b['call_time'] ??
                            b['timestamp'] ??
                            b['time'] ??
                            b['created_at'];

                        if (rawTsA is Timestamp) {
                          timeA = rawTsA.toDate();
                        } else if (rawTsA is int) {
                          timeA = DateTime.fromMillisecondsSinceEpoch(rawTsA);
                        } else if (rawTsA is String) {
                          try {
                            timeA = DateTime.parse(rawTsA);
                          } catch (_) {}
                        }

                        if (rawTsB is Timestamp) {
                          timeB = rawTsB.toDate();
                        } else if (rawTsB is int) {
                          timeB = DateTime.fromMillisecondsSinceEpoch(rawTsB);
                        } else if (rawTsB is String) {
                          try {
                            timeB = DateTime.parse(rawTsB);
                          } catch (_) {}
                        }

                        if (timeA == null || timeB == null) return 0;
                        return timeB.compareTo(timeA); // Newest first
                      });
                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: sortedLogs.map((log) {
                          final data = log.data();
                          final transcript = data['transcript'] ?? '';

                          DateTime? ts;
                          final rawTs =
                              data['call_time'] ??
                              data['timestamp'] ??
                              data['time'] ??
                              data['created_at'];
                          if (rawTs is Timestamp) {
                            ts = rawTs.toDate();
                          } else if (rawTs is int) {
                            ts = DateTime.fromMillisecondsSinceEpoch(rawTs);
                          } else if (rawTs is String) {
                            try {
                              ts = DateTime.parse(rawTs);
                            } catch (_) {
                              ts = null;
                            }
                          }

                          final dynDur =
                              data['duration'] ??
                              data['length'] ??
                              data['duration_seconds'];
                          String durationText = '';
                          if (dynDur != null) {
                            if (dynDur is num) {
                              durationText = '${dynDur}s';
                            } else {
                              durationText = dynDur.toString();
                            }
                          }

                          final timeStr = ts != null
                              ? DateFormat('HH:mm:ss dd MMM yyyy').format(ts)
                              : '';

                          // resolve audio path (several possible field names)
                          final audioPathRaw =
                              data['file_path'] ??
                              data['local_path'] ??
                              data['audio_path'] ??
                              data['path'] ??
                              data['file'] ??
                              data['audio_file'] ??
                              data['filePath'] ??
                              data['audio'] ??
                              data['file_uri'] ??
                              data['fileUri'] ??
                              data['audio_uri'] ??
                              data['audioUri'];
                          String? audioPath;
                          if (audioPathRaw is String &&
                              audioPathRaw.isNotEmpty) {
                            audioPath = audioPathRaw;
                            if (audioPath.startsWith('file://')) {
                              audioPath = audioPath.replaceFirst('file://', '');
                            }
                          }

                          return Padding(
                            padding: const EdgeInsets.symmetric(vertical: 8.0),
                            child: Container(
                              width: double.infinity,
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: Colors.white,
                                borderRadius: BorderRadius.circular(8),
                                border: Border.all(color: Colors.black12),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  if (timeStr.isNotEmpty)
                                    Row(
                                      children: [
                                        Text(
                                          timeStr,
                                          style: const TextStyle(
                                            fontSize: 12,
                                            color: Colors.black54,
                                          ),
                                        ),
                                        if (durationText.isNotEmpty) ...[
                                          const SizedBox(width: 8),
                                          Text(
                                            durationText,
                                            style: const TextStyle(
                                              fontSize: 12,
                                              color: Colors.black54,
                                            ),
                                          ),
                                        ],
                                      ],
                                    ),
                                  if (timeStr.isNotEmpty)
                                    const SizedBox(height: 8),

                                  // audio bar (local file) or fallback message
                                  if (audioPath != null)
                                    FutureBuilder<String?>(
                                      future: _resolveLocalAudioPath(audioPath),
                                      builder: (context, snap) {
                                        if (snap.connectionState !=
                                            ConnectionState.done) {
                                          return const SizedBox(
                                            height: 56,
                                            child: Center(
                                              child:
                                                  CircularProgressIndicator(),
                                            ),
                                          );
                                        }
                                        final resolved = snap.data;
                                        if (resolved == null ||
                                            resolved.isEmpty) {
                                          return const Text(
                                            'no file path found',
                                            style: TextStyle(
                                              color: Colors.black54,
                                            ),
                                          );
                                        }
                                        return AudioPlayerBar(
                                          source: resolved,
                                          isLocal: true,
                                        );
                                      },
                                    )
                                  else
                                    const Text(
                                      'no file path found',
                                      style: TextStyle(color: Colors.black54),
                                    ),

                                  const SizedBox(height: 8),
                                  Text(transcript),
                                  const SizedBox(height: 8),
                                  const Divider(
                                    height: 1,
                                    color: Colors.black12,
                                  ),
                                ],
                              ),
                            ),
                          );
                        }).toList(),
                      );
                    },
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
