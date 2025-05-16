from firebase_functions import firestore_fn
from firebase_admin import initialize_app, firestore, messaging
from firebase_admin import exceptions

initialize_app()

@firestore_fn.on_document_created(
    document="geofence_events/{ownerUid}/events/{eventId}"
)
def on_geofence_event(event: firestore_fn.Event[firestore.DocumentSnapshot]):
    print("🚀 [on_geofence_event] Triggered")

    try:
        # 1. 轉換 snapshot
        data = event.data.to_dict() or {}
        print(f"📦 [Data Snapshot] {data}")

        ownerUid = event.params.get("ownerUid") # 觸發事件的人
        locationName = data.get("locationName")
        fenceId = data.get("fenceId")
        action = data.get("action")
        targetUid = data.get("targetUid") #要被發送通知的人
        print(f"[Target UID] {targetUid}")

        if not (fenceId and action and targetUid):
            print("⚠️ [Skip] Missing fenceId/action/targetUid")
            return

        print(f"[Owner UID] 觸發者 {ownerUid}")
        print(f"[Fence ID] {fenceId}, [Action] {action}, [TargetUid UID] {targetUid}")

        # 2. 查 geofence config
        cfgSnap = firestore.client() \
            .collection("users") \
            .document(ownerUid) \
            .collection("geofences") \
            .document(fenceId) \
            .get()

        if not cfgSnap.exists:
            print("[Error] Geofence config not found")
            return

        cfgData = cfgSnap.to_dict() or {}
        print(f"[Geofence Config] {cfgData}")

        targetSnap = firestore.client() \
                    .collection("users") \
                    .document(targetUid) \
                    .get()

        targetData = targetSnap.to_dict() or {}
        token = targetData.get("fcmToken")

        if not token:
            print("[Error] No FCM token found")
            return
        print("接收通知者 email:"+targetData.get("email"))
        print("接收通知者 name:"+targetData.get("displayName"))
        print("接收通知者 token:"+token)

        userSnap = firestore.client() \
            .collection("users") \
            .document(ownerUid) \
            .get()

        if not userSnap.exists:
            print(f"[Error] User snapshot for {ownerUid} not found")
            return

        userData = userSnap.to_dict() or {}
        print(f"[觸發者 Data] {userData}")

        userNickName = userData.get("displayName", "未知使用者")
        photoUri = userData.get("photoUri")

        # 4. 發送通知
        notifyTitle = f"地理圍籬：{userNickName}{'進入' if action == 'enter' else '離開'} {locationName or ''}"
        print(f"[Sending FCM] Title: {notifyTitle}")

        message = messaging.Message(
            token=token,
            notification=messaging.Notification(
                title=notifyTitle
            ),
            data={
                "fenceId": fenceId,
                "action": action,
                "targetUid": targetUid,
                "notifyName": notifyTitle,
                "photoUri":photoUri
            }
        )

        try:
            response = messaging.send(message)
            print(f"[FCM Sent] {response}")
        except exceptions.FirebaseError as e:
            # 所有從 Firebase Admin SDK 層級包裝過的錯誤
            print(f"⚠[FirebaseError] {e}")
            # 如果裡面 message 或 code 有「NotFound」、或「invalid-argument」字樣，就清掉 token
            if "not found" in str(e).lower() or "invalid-argument" in str(e).lower():
                firestore.client() \
                    .collection("users") \
                    .document(targetUid) \
                    .update({"fcmToken": firestore.DELETE_FIELD})
                print(f"清除了 {targetUid} 的失效 token")

    except Exception as e:
        print(f"[Unhandled Exception] {e}")
