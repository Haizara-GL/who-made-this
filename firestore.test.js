const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const EVE_UID = "eve_uid_003";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastDate = (offsetMs = 5000) => new Date(Date.now() - offsetMs);
const futureDate = (offsetMs = 60000) => new Date(Date.now() + offsetMs);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- 1. UNAUTHENTICATED DENIAL ACROSS ALL COLLECTIONS ---

test("Unauthenticated users cannot read or write any collection", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("users").doc(ALICE_UID).get());
  await assertFails(db.collection("public_profiles").where("discoverable", "==", true).get());
  await assertFails(db.collection("chats").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(db.collection("calls").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(db.collection("parking_spots").where("publicFeed", "==", true).get());
});

// --- 2. PRIVATE USERS (PII ISOLATION) & PUBLIC PROFILES ---

test("Private users isolate PII strictly to owner and reject ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Алиса Смирнова",
      handle: "alisa_s",
      email: "alisa@example.com",
      carInfo: "Белый Geely • А777АА",
      city: "Москва",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Owner can read & update
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      carInfo: "Серый Haval • В123ОР",
      updatedAt: pastDate(1000),
    })
  );

  // Another user cannot read, list, or modify Alice's private PII
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Взлом",
      updatedAt: pastDate(1000),
    })
  );

  // Reject ghost field / @DocumentId 'id' leak & future timestamp
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      id: BOB_UID,
      userId: BOB_UID,
      displayName: "Боб",
      handle: "bob_1",
      email: "bob@example.com",
      carInfo: "",
      city: "Казань",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Боб",
      handle: "bob_1",
      email: "bob@example.com",
      carInfo: "",
      city: "Казань",
      createdAt: futureDate(),
      updatedAt: futureDate(),
    })
  );
});

test("Public profiles support discoverable directory query and block counter cheating", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Алиса",
      handle: "alisa",
      carInfo: "Белый кроссовер",
      city: "Москва",
      discoverable: true,
      spotsShared: 0,
      spotsCaught: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Filtered directory query matches Kotlin repository
  await assertSucceeds(
    bobDb.collection("public_profiles").where("discoverable", "==", true).limit(50).get()
  );
  // Unfiltered list fails
  await assertFails(bobDb.collection("public_profiles").get());

  // Increment by +1 allowed; jumping by +100 rejected
  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      spotsShared: 1,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      spotsShared: 100,
      updatedAt: pastDate(1000),
    })
  );
});

// --- 3. CHATS & MESSAGES ---

test("Chats & messages enforce membership, invite code joining, and query parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const ts = pastDate();

  // Private direct chat between Alice and Bob
  await assertSucceeds(
    aliceDb.collection("chats").doc("direct_1").set({
      title: "Боб",
      chatType: "direct",
      creatorId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      inviteCode: "",
      joinable: false,
      lastMessageText: "Диалог открыт",
      lastMessageSender: "Алиса",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice sends a message
  await assertSucceeds(
    aliceDb.collection("chats").doc("direct_1").collection("messages").doc("msg_1").set({
      chatId: "direct_1",
      senderId: ALICE_UID,
      senderName: "Алиса",
      text: "Привет! Освобождаю место у подъезда",
      messageType: "text",
      spotAddress: "",
      spotId: "",
      memberUids: [ALICE_UID, BOB_UID],
      publicChannel: false,
      createdAt: pastDate(2000),
    })
  );

  // Bob lists his chats and reads private chat messages with memberUids filter
  await assertSucceeds(
    bobDb.collection("chats").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("direct_1").collection("messages")
      .where("memberUids", "array-contains", BOB_UID).get()
  );

  // Eve cannot read private chat or its messages
  await assertFails(eveDb.collection("chats").doc("direct_1").get());
  await assertFails(
    eveDb.collection("chats").doc("direct_1").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    eveDb.collection("chats").doc("direct_1").collection("messages").get()
  );
  await assertFails(
    eveDb.collection("chats").doc("direct_1").collection("messages")
      .where("memberUids", "array-contains", ALICE_UID).get()
  );
  const eveSelfSnap = await assertSucceeds(
    eveDb.collection("chats").doc("direct_1").collection("messages")
      .where("memberUids", "array-contains", EVE_UID).get()
  );
  if (eveSelfSnap.size !== 0) {
    throw new Error(`Expected 0 leaked messages for Eve, got ${eveSelfSnap.size}`);
  }
  // Eve cannot send messages into direct_1
  await assertFails(
    eveDb.collection("chats").doc("direct_1").collection("messages").doc("msg_evil").set({
      chatId: "direct_1",
      senderId: EVE_UID,
      senderName: "Ева",
      text: "Спам",
      messageType: "text",
      spotAddress: "",
      spotId: "",
      memberUids: [ALICE_UID, BOB_UID],
      publicChannel: false,
      createdAt: pastDate(1000),
    })
  );

  // Joinable parking district channel
  await assertSucceeds(
    aliceDb.collection("chats").doc("park_arbat").set({
      title: "Парковка Арбат",
      chatType: "parking",
      creatorId: ALICE_UID,
      memberUids: [ALICE_UID],
      inviteCode: "ARBAT7",
      joinable: true,
      lastMessageText: "Канал создан",
      lastMessageSender: "Алиса",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob looks up joinable channel by inviteCode & joins cleanly
  await assertSucceeds(
    bobDb.collection("chats")
      .where("joinable", "==", true)
      .where("inviteCode", "==", "ARBAT7")
      .limit(1)
      .get()
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("park_arbat").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(1000),
    })
  );
});

// --- 4. VOICE & VIDEO CALLS (TERMINAL STATE LOCK) ---

test("Calls enforce participant access, valid state transitions, and terminal state lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      callerName: "Алиса",
      calleeId: BOB_UID,
      calleeName: "Боб",
      participantUids: [ALICE_UID, BOB_UID],
      callType: "audio",
      status: "ringing",
      durationSeconds: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Eve cannot read or tamper with the call
  await assertFails(eveDb.collection("calls").doc("call_1").get());
  await assertFails(
    eveDb.collection("calls").doc("call_1").update({
      status: "ended",
      durationSeconds: 10,
      updatedAt: pastDate(1000),
    })
  );

  // Participant filtered list succeeds; unfiltered list fails
  await assertSucceeds(
    bobDb.collection("calls").where("participantUids", "array-contains", BOB_UID).get()
  );
  await assertFails(bobDb.collection("calls").get());

  // Bob answers call -> active
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "active",
      updatedAt: pastDate(2000),
    })
  );

  // Alice ends call -> ended
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ended",
      durationSeconds: 42,
      updatedAt: pastDate(1000),
    })
  );

  // Terminal state locked: further updates must fail!
  await assertFails(
    aliceDb.collection("calls").doc("call_1").update({
      status: "active",
      updatedAt: pastDate(500),
    })
  );
});

// --- 5. LIVE PARKING CATCHER SPOTS ---

test("Parking spots enforce lifecycle, catching, releasing, completing, and terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("parking_spots").doc("spot_1").set({
      posterId: ALICE_UID,
      posterName: "Алиса",
      posterCar: "Белый Geely",
      address: "ул. Тверская, 14",
      district: "Центр",
      parkingKind: "paid",
      note: "Выезжаю через 5 минут, место широкое",
      latitude: 55.7615,
      longitude: 37.6095,
      hasCoordinates: true,
      leaveInMinutes: 5,
      status: "leaving_soon",
      publicFeed: true,
      claimedByUid: "",
      claimedByName: "",
      claimedByCar: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Public feed query matches Kotlin repository; unfiltered list rejected
  await assertSucceeds(
    bobDb.collection("parking_spots").where("publicFeed", "==", true).limit(100).get()
  );
  await assertFails(bobDb.collection("parking_spots").get());

  // Bob catches the spot
  await assertSucceeds(
    bobDb.collection("parking_spots").doc("spot_1").update({
      status: "claimed",
      claimedByUid: BOB_UID,
      claimedByName: "Боб",
      claimedByCar: "Чёрный Solaris",
      updatedAt: pastDate(3000),
    })
  );

  // Eve cannot steal or release Bob's claimed spot
  await assertFails(
    eveDb.collection("parking_spots").doc("spot_1").update({
      status: "claimed",
      claimedByUid: EVE_UID,
      claimedByName: "Ева",
      claimedByCar: "Красный Polo",
      updatedAt: pastDate(2000),
    })
  );
  await assertFails(
    eveDb.collection("parking_spots").doc("spot_1").update({
      status: "free",
      claimedByUid: "",
      claimedByName: "",
      claimedByCar: "",
      updatedAt: pastDate(2000),
    })
  );

  // Bob completes parking -> completed (terminal state)
  await assertSucceeds(
    bobDb.collection("parking_spots").doc("spot_1").update({
      status: "completed",
      updatedAt: pastDate(1000),
    })
  );

  // Terminal state locked: nobody can mutate completed spot
  await assertFails(
    aliceDb.collection("parking_spots").doc("spot_1").update({
      status: "free",
      claimedByUid: "",
      claimedByName: "",
      claimedByCar: "",
      updatedAt: pastDate(500),
    })
  );
});
