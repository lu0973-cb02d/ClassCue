import wearengine from '@system.wearengine';
import { P2pClient } from './wearengine';

/**
 * Compatibility facade for WATCH GT 5 Pro service 5.0.2.202.
 *
 * Huawei's 5.0.2.306 JS wrapper calls unsubscribeMsg() without an options
 * object immediately before subscribeMsg(). Service 5.0.2.202 dereferences
 * the missing callback and reports "onSuccess of undefined". Keep the
 * official SDK file byte-for-byte intact and isolate the workaround here.
 */
var CompatP2pClient = (function () {
  function CompatP2pClient() {
    this.core = new P2pClient();
  }

  CompatP2pClient.prototype.setPeerPkgName = function (peerPkgName) {
    this.core.setPeerPkgName(peerPkgName);
  };

  CompatP2pClient.prototype.setPeerFingerPrint = function (fingerPrint) {
    this.core.setPeerFingerPrint(fingerPrint);
  };

  CompatP2pClient.prototype.registerReceiver = function (receiver) {
    if (!receiver || typeof receiver.onReceiveMessage !== 'function') {
      return;
    }
    var successCallback = function (data) {
      if (!data || data.isRegister) {
        if (typeof receiver.onSuccess === 'function') {
          receiver.onSuccess();
        }
      } else if (data.isFileType) {
        receiver.onReceiveMessage({
          isFileType: true,
          name: data.file,
          mode: '',
          mode2: ''
        });
      } else {
        receiver.onReceiveMessage(data.message);
      }
    };
    wearengine.subscribeMsg({
      success: successCallback,
      fail: function (data, code) {
        if (typeof receiver.onFailure === 'function') {
          receiver.onFailure(data, code);
        }
      }
    });
  };

  CompatP2pClient.prototype.unregisterReceiver = function (receiver) {
    // The page process releases its subscription. Avoid the unstable native
    // unsubscribe bridge on service 5.0.2.202.
    if (receiver && typeof receiver.onSuccess === 'function') {
      receiver.onSuccess();
    }
  };

  return CompatP2pClient;
})();

export { CompatP2pClient };
