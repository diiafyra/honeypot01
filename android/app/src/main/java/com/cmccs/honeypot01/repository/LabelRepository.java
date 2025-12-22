package cmc.cs.honeypot01.repository;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.List;

public class LabelRepository {

    public static List<String> getAllLabelsBlocking() throws Exception {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        QuerySnapshot snapshot =
                Tasks.await(db.collection("labels").get());

        List<String> labels = new ArrayList<>();
        for (QueryDocumentSnapshot doc : snapshot) {
            labels.add(doc.getId());
        }
        return labels;
    }
}
