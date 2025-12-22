package cmc.cs.honeypot01.repository;

import cmc.cs.honeypot01.model.CallDetailsHolder;
import cmc.cs.honeypot01.model.SpamNumber;
import com.google.firebase.firestore.*;

import java.util.HashMap;
import java.util.Map;

public class SpamNumbersRepository {

    private final FirebaseFirestore db;

    public SpamNumbersRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public void upsert(CallDetailsHolder call) {
        String number = call.getPhoneNumber();
        DocumentReference ref = db.collection("spam_numbers").document(number);

        SpamNumber init = new SpamNumber(
                number,
                call.getVerificationStatus(),
                call.getHandlePresentation(),
                call.getCallerDisplayName()
        );

        ref.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                ref.set(init, SetOptions.merge());
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("call_count", FieldValue.increment(1));
            updates.put("last_seen", System.currentTimeMillis());

            ref.update(updates);
        });
    }
    public void updateLabel(String spamNumber, String label) {

        Map<String, Object> updates = new HashMap<>();
        updates.put("label", label);
        updates.put("last_update", System.currentTimeMillis());

        db.collection("spam_numbers")
                .document(spamNumber)
                .update(updates);
    }
}
