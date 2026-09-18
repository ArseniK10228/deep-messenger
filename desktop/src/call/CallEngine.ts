export type IceConfig = RTCIceServer;

export class CallEngine {
  private pc: RTCPeerConnection | null = null;
  private localStream: MediaStream | null = null;
  private remoteAudio = new Audio();
  private remoteAudioEl: HTMLAudioElement | null = null;

  onIceCandidate: ((c: RTCIceCandidate) => void) | null = null;
  onConnectionChange: ((connected: boolean) => void) | null = null;

  bindRemoteElement(el: HTMLAudioElement) {
    this.remoteAudioEl = el;
    if (this.remoteAudio.srcObject) {
      el.srcObject = this.remoteAudio.srcObject;
    }
  }

  async start(iceServers: IceConfig[]) {
    await this.stop();
    this.remoteAudio.autoplay = true;
    this.pc = new RTCPeerConnection({ iceServers });
    this.pc.onicecandidate = (ev) => {
      if (ev.candidate) this.onIceCandidate?.(ev.candidate);
    };
    this.pc.ontrack = (ev) => {
      const stream = ev.streams[0] ?? new MediaStream([ev.track]);
      this.remoteAudio.srcObject = stream;
      if (this.remoteAudioEl) this.remoteAudioEl.srcObject = stream;
      void this.remoteAudio.play().catch(() => {});
    };
    this.pc.onconnectionstatechange = () => {
      const connected = this.pc?.connectionState === 'connected';
      this.onConnectionChange?.(connected);
    };
    this.localStream = await navigator.mediaDevices.getUserMedia({
      audio: true,
      video: false
    });
    for (const track of this.localStream.getTracks()) {
      this.pc.addTrack(track, this.localStream);
    }
  }

  async createOffer(): Promise<RTCSessionDescriptionInit> {
    if (!this.pc) throw new Error('no pc');
    const offer = await this.pc.createOffer({ offerToReceiveAudio: true });
    await this.pc.setLocalDescription(offer);
    return offer;
  }

  async createAnswer(): Promise<RTCSessionDescriptionInit> {
    if (!this.pc) throw new Error('no pc');
    const answer = await this.pc.createAnswer();
    await this.pc.setLocalDescription(answer);
    return answer;
  }

  async setRemoteDescription(sdp: string, type: RTCSdpType) {
    if (!this.pc) throw new Error('no pc');
    await this.pc.setRemoteDescription({ type, sdp });
  }

  async addIceCandidate(candidate: string, sdpMid: string | null, sdpMLineIndex: number | null) {
    if (!this.pc) return;
    try {
      await this.pc.addIceCandidate(
        new RTCIceCandidate({
          candidate,
          sdpMid,
          sdpMLineIndex: sdpMLineIndex ?? undefined
        })
      );
    } catch {
      /* ignore late ice */
    }
  }

  setMuted(muted: boolean) {
    this.localStream?.getAudioTracks().forEach((t) => {
      t.enabled = !muted;
    });
  }

  async stop() {
    this.localStream?.getTracks().forEach((t) => t.stop());
    this.localStream = null;
    this.pc?.close();
    this.pc = null;
    this.remoteAudio.srcObject = null;
    if (this.remoteAudioEl) this.remoteAudioEl.srcObject = null;
  }
}
