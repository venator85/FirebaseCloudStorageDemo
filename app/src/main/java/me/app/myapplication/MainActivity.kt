package me.app.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import me.app.myapplication.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var operation: (FirebaseUser) -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRead.setOnClickListener {
            runOp { user ->
                val file = fileRef(user)
                file.getBytes(Long.MAX_VALUE).addOnSuccessListener {
                    val content = it.decodeToString()
                    Log.e(TAG, "file download completed: $content")
                    binding.textfield.setText(content)
                }.addOnFailureListener {
                    Log.e(TAG, "file download error", it)
                    binding.textfield.setText("Error")
                }
            }
        }

        binding.btnWrite.setOnClickListener {
            runOp { user ->
                val file = fileRef(user)
                val data = binding.textfield.text?.toString().orEmpty().encodeToByteArray()

                file.putBytes(data)
                    .addOnSuccessListener {
                        Toast.makeText(this@MainActivity, "Write OK", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener {
                        Log.e(TAG, "file creation error", it)
                        Toast.makeText(this@MainActivity, "Write failed!", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun fileRef(user: FirebaseUser): StorageReference {
        return Firebase.storage.reference
            .child("mydata")
            .child(user.uid)
            .child("foo.txt")
    }

    private fun runOp(op: (FirebaseUser) -> Unit) {
        operation = op
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            signIn()
        } else {
            signInOk(currentUser)
        }
    }

    private fun signIn() {
        val signInIntent = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        ).signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            GoogleSignIn.getSignedInAccountFromIntent(data)
                .onSuccessTask { account ->
                    Log.e(TAG, "firebaseAuthWithGoogle: id " + account.id)
                    Log.e(TAG, "firebaseAuthWithGoogle: email " + account.email)
                    val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
                    Firebase.auth.signInWithCredential(credential)
                }.onSuccessTask {
                    val user = Firebase.auth.currentUser
                    if (user != null) {
                        Tasks.forResult(user)
                    } else {
                        Tasks.forException(Exception("user null after signin"))
                    }
                }.addOnSuccessListener {
                    Log.e(TAG, "signInWithCredential:success, uid: ${it.uid}, email: ${it.email}")
                    signInOk(it)
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "signInWithCredential:failure", exception)
                }
        }
    }

    private fun signInOk(user: FirebaseUser) {
        operation.invoke(user)
    }

    companion object {
        private const val TAG = "GoogleActivity"
        private const val RC_SIGN_IN = 9001
    }
}
