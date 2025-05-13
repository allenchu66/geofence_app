from firebase_functions import firestore_fn
from firebase_admin import initialize_app, firestore, messaging

initialize_app()

@firestore_fn.on_document_created(
    document="geofence_events/{ownerUid}/events/{eventId}"
)
def on_geofence_event(event: firestore_fn.Event[dict]):
    # event.data 是 DocumentSnapshot，先轉成 dict
    snap = event.data
    data = snap.to_dict() or {}

    ownerUid   = event.params["ownerUid"]
    locationName = data.get("locationName")
    fenceId    = data.get("fenceId")
    action     = data.get("action")
    triggerUid = data.get("triggerUid")

    # 如果任一欄位不存在，提前結束
    if not (fenceId and action and triggerUid):
        return

    cfgSnap = firestore.client() \
            .collection("users") \
            .document(ownerUid) \
            .collection("geofences") \
            .document(fenceId) \
            .get()
    if not cfgSnap.exists:
        return
    cfgData    = cfgSnap.to_dict() or {}
    targetUid  = cfgData.get("targetUid")
    # 2. 拿 targetUid 的 FCM token
    userSnap = firestore.client() \
        .collection("users") \
        .document(targetUid) \
        .get()
    userData = userSnap.to_dict() or {}
    token = userData.get("fcmToken")
    userNickName = userData.get("displayName")
    if not token:
        return

    # 3. 發送通知
    message = messaging.Message(
        token=token,
        notification=messaging.Notification(
            title=f"地理圍籬：{userNickName}{'進入' if action == 'enter' else '離開'} {locationName}"
        ),
        data={
            "fenceId":    fenceId,
            "action":     action,
            "triggerUid": triggerUid,
            "notifyName": notifyName or ""
        }
    )
    messaging.send(message)
