import 'package:flutter/material.dart';
import 'package:just_audio/just_audio.dart';
import 'package:audio_session/audio_session.dart';

class AudioPlayerBar extends StatefulWidget {
  final String
  source; // http(s) url, gs:// url, storage path or local file path
  final bool isLocal;
  const AudioPlayerBar({super.key, required this.source, this.isLocal = false});

  @override
  State<AudioPlayerBar> createState() => _AudioPlayerBarState();
}

class _AudioPlayerBarState extends State<AudioPlayerBar> {
  late final AudioPlayer _player;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _player = AudioPlayer();
    _init();
  }

  Future<void> _init() async {
    try {
      final session = await AudioSession.instance;
      await session.configure(const AudioSessionConfiguration.speech());
      if (widget.isLocal) {
        await _player.setFilePath(widget.source);
      } else {
        await _player.setUrl(widget.source);
      }
    } catch (e) {
      _error = e.toString();
    } finally {
      setState(() => _loading = false);
    }
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  String _fmt(Duration d) {
    final mm = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final ss = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$mm:$ss';
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const SizedBox(
        height: 64,
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (_error != null) {
      return SizedBox(
        height: 64,
        child: Center(child: Text('Lỗi âm thanh: $_error')),
      );
    }
    return StreamBuilder<PlayerState>(
      stream: _player.playerStateStream,
      builder: (context, snapshot) {
        final playing = snapshot.data?.playing ?? false;
        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                IconButton(
                  iconSize: 36,
                  icon: Icon(playing ? Icons.pause_circle : Icons.play_circle),
                  onPressed: () {
                    if (playing) {
                      _player.pause();
                    } else {
                      _player.play();
                    }
                  },
                ),
                Expanded(
                  child: StreamBuilder<Duration>(
                    stream: _player.positionStream,
                    builder: (context, posSnap) {
                      final pos = posSnap.data ?? Duration.zero;
                      return StreamBuilder<Duration?>(
                        stream: _player.durationStream,
                        builder: (context, durSnap) {
                          final dur = durSnap.data ?? Duration.zero;
                          final maxMs = dur.inMilliseconds > 0
                              ? dur.inMilliseconds
                              : 1;
                          final value = pos.inMilliseconds
                              .clamp(0, maxMs)
                              .toDouble();
                          return Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Slider(
                                min: 0,
                                max: maxMs.toDouble(),
                                value: value,
                                onChanged: dur == Duration.zero
                                    ? null
                                    : (v) => _player.seek(
                                        Duration(milliseconds: v.toInt()),
                                      ),
                              ),
                              Row(
                                mainAxisAlignment:
                                    MainAxisAlignment.spaceBetween,
                                children: [
                                  Text(
                                    _fmt(pos),
                                    style: const TextStyle(fontSize: 12),
                                  ),
                                  Text(
                                    _fmt(dur),
                                    style: const TextStyle(fontSize: 12),
                                  ),
                                ],
                              ),
                            ],
                          );
                        },
                      );
                    },
                  ),
                ),
              ],
            ),
          ],
        );
      },
    );
  }
}
