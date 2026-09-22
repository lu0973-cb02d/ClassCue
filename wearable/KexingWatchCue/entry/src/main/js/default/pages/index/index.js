import storage from '@system.storage';
import vibrator from '@system.vibrator';
import { CompatP2pClient } from '../../wearengine/compat';
import {
  HISTORY_STORAGE_KEY,
  MAX_HISTORY_ITEMS,
  PHONE_APP_FINGERPRINT,
  PHONE_APP_PACKAGE_NAME
} from '../../constants/peer';
import { mergeIncoming, normalizeRecords, parseIncoming } from './history';

var p2pClient = new CompatP2pClient();

export default {
  data: {
    records: [],
    currentIndex: 0,
    hasRecords: false,
    hasFast: false,
    hasStrong: false,
    pageLabel: '0 / 0',
    statusText: '等待手机消息',
    currentTitle: '课醒',
    currentQuestion: '',
    currentFastAnswer: '',
    currentFastModel: '',
    currentStrongAnswer: '',
    currentStrongModel: ''
  },

  onInit() {
    this.loadHistory();
    this.registerReceiver();
  },

  onShow() {
    console.info('Kexing page shown');
  },

  onHide() {
    console.info('Kexing page hidden');
  },

  onDestroy() {
    p2pClient.unregisterReceiver({
      onSuccess: function () {
        console.info('Kexing receiver unregistered');
      }
    });
  },

  loadHistory() {
    var that = this;
    storage.get({
      key: HISTORY_STORAGE_KEY,
      default: '[]',
      success: function (data) {
        try {
          that.records = normalizeRecords(JSON.parse(data), MAX_HISTORY_ITEMS);
        } catch (error) {
          that.records = [];
        }
        that.currentIndex = 0;
        that.refreshPage();
      },
      fail: function () {
        that.records = [];
        that.refreshPage();
      }
    });
  },

  saveHistory() {
    storage.set({
      key: HISTORY_STORAGE_KEY,
      value: JSON.stringify(this.records),
      success: function () {
        console.info('Kexing history saved');
      },
      fail: function (data, code) {
        console.error('Kexing history save failed: ' + code + ' ' + data);
      }
    });
  },

  registerReceiver() {
    var that = this;
    p2pClient.setPeerPkgName(PHONE_APP_PACKAGE_NAME);
    p2pClient.setPeerFingerPrint(PHONE_APP_FINGERPRINT);
    p2pClient.registerReceiver({
      onSuccess: function () {
        that.statusText = '已连接 · 等待消息';
      },
      onFailure: function () {
        that.statusText = '连接失败 · 检查配对';
      },
      onReceiveMessage: function (raw) {
        var incoming = parseIncoming(raw);
        if (!incoming) {
          that.statusText = '消息格式错误';
          return;
        }
        if (incoming.kind === 'alert') {
          that.statusText = incoming.title || '课堂提醒';
          if (incoming.vibrate !== false) {
            that.vibrateOnce();
          }
          return;
        }
        that.records = mergeIncoming(that.records, incoming, MAX_HISTORY_ITEMS);
        that.currentIndex = 0;
        that.refreshPage();
        that.saveHistory();
        if (incoming.vibrate !== false) {
          that.vibrateOnce();
        } else {
          that.statusText = '静默同步 · 旋钮查看';
        }
      }
    });
  },

  vibrateOnce() {
    vibrator.vibrate({
      mode: 'short',
      success: function () {
        console.info('Kexing vibration success');
      },
      fail: function (data, code) {
        console.error('Kexing vibration failed: ' + code + ' ' + data);
      }
    });
  },

  refreshPage() {
    var count = this.records.length;
    this.hasRecords = count > 0;
    if (!this.hasRecords) {
      this.currentIndex = 0;
      this.pageLabel = '0 / 0';
      this.currentTitle = '课醒';
      this.currentQuestion = '';
      this.currentFastAnswer = '';
      this.currentStrongAnswer = '';
      this.hasFast = false;
      this.hasStrong = false;
      return;
    }
    if (this.currentIndex < 0) {
      this.currentIndex = 0;
    }
    if (this.currentIndex >= count) {
      this.currentIndex = count - 1;
    }
    var item = this.records[this.currentIndex];
    this.pageLabel = (this.currentIndex + 1) + ' / ' + count;
    this.currentTitle = item.title || '课堂问题';
    this.currentQuestion = item.question || '等待问题原文';
    this.currentFastAnswer = item.fastAnswer || '';
    this.currentFastModel = item.fastModel || '';
    this.currentStrongAnswer = item.strongAnswer || '';
    this.currentStrongModel = item.strongModel || '';
    this.hasFast = this.currentFastAnswer.length > 0;
    this.hasStrong = this.currentStrongAnswer.length > 0;
    this.statusText = this.hasFast || this.hasStrong ? '上下滚动 · 左右翻题' : '已收题 · 等待答案';
  },

  newerPage() {
    if (this.currentIndex > 0) {
      this.currentIndex--;
      this.refreshPage();
    }
  },

  olderPage() {
    if (this.currentIndex + 1 < this.records.length) {
      this.currentIndex++;
      this.refreshPage();
    }
  },

  swipeEvent(event) {
    if (event.direction === 'left') {
      this.olderPage();
    } else if (event.direction === 'right') {
      this.newerPage();
    }
  }
}
