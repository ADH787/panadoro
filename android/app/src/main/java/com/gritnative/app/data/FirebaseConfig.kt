package com.gritnative.app.data

/** Firebase project constants shared by auth and Firestore. The API key is a
 * public client identifier, not a secret; access is controlled by rules and the
 * signed-in user's ID token. */
object FirebaseConfig {
    const val PROJECT_ID = "habit-tracker-bd354"
    const val API_KEY = "AIzaSyAc97ILytNHNXRbS_EapcllNLd1CcaoLPo"
    const val DATABASE = "default"
    const val COLLECTION = "panadora"
}
