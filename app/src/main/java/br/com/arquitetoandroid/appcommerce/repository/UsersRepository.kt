package br.com.arquitetoandroid.appcommerce.repository

import android.app.Application
import android.net.Uri
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import br.com.arquitetoandroid.appcommerce.R
import br.com.arquitetoandroid.appcommerce.database.AppDatabase
import br.com.arquitetoandroid.appcommerce.model.User
import br.com.arquitetoandroid.appcommerce.model.UserAddress
import br.com.arquitetoandroid.appcommerce.model.UserWithAddresses
import br.com.arquitetoandroid.appcommerce.viewmodel.UserViewModel
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.firestore.FirebaseFirestore
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.NetworkImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.storage.ktx.storage
import org.json.JSONObject

class UsersRepository (application: Application) {

    private val firestore = FirebaseFirestore.getInstance()
    private val queue = Volley.newRequestQueue(application)

    private val preference = PreferenceManager.getDefaultSharedPreferences(application)

    private val glide = Glide.with(application) // trabalho com cache de imagens
    private val storege = Firebase.storage(Firebase.app)

    fun login(email: String, password: String) : LiveData<User>{
        val liveData = MutableLiveData<User>(null)
        val params  = JSONObject().also{
            it.put("email", email)
            it.put("password", password)
            it.put("returnSecureToken", true)
        }
        val request = JsonObjectRequest(Request.Method.POST,
            BASE_URL + SIGNIN + KEY,
            params,
            Response.Listener { response ->
                val localId = response.getString("localId")
                val idToken = response.getString("idToken")

                // adicionando no banco de dados
                firestore.collection("users").document(localId).get().addOnSuccessListener {
                    val user = it.toObject(User::class.java)
                    user?.id = localId
                    user?.password = idToken

                    liveData.value = user
                    preference.edit().putString(UserViewModel.USER_ID, localId).apply() // adiconado dados de login localmente

                    firestore.collection("users").document(localId).set(user!!)

                }
            },
            Response.ErrorListener { error ->
                Log.e(this.toString(), error.message ?: "Error")
            }
        )
        queue.add(request)
        return liveData
    }

    fun createUser(user: User){
        val params  = JSONObject().also{
            it.put("email", user.email)
            it.put("password", user.password)
            it.put("returnSecureToken", true)
        }
        val request = JsonObjectRequest(Request.Method.POST,
            BASE_URL + SIGNUP + KEY,
            params,
            Response.Listener { response ->
                user.id = response.getString("localId")
                user.password = response.getString("idToken")

                // adicionando no banco de dados
                firestore.collection("users").document(user.id).set(user)
                    .addOnSuccessListener {
                        Log.d(this.toString(), "Usuário ${user.email} cadastrado com sucesso!")
                    }
            },
            Response.ErrorListener { error ->
                Log.e(this.toString(), error.message ?: "Error")
            }
        )
        queue.add(request)
    }

    fun load(userID: String): LiveData<UserWithAddresses>{
        val userWithAddresses = UserWithAddresses()
        val liveData = MutableLiveData<UserWithAddresses>()

        val userRef = firestore.collection("users").document(userID)
        userRef.get().addOnSuccessListener {
            var user = it.toObject(User::class.java)
            user?.id = it.id

            userWithAddresses.user = user!!

            userRef.collection("addresses").get().addOnCompleteListener { snap->
                snap.result?.forEach{ doc->
                    val addresses = doc.toObject(UserAddress::class.java)
                    addresses.id = doc.id
                    userWithAddresses.addresses.add(addresses)

                }
                liveData.value = userWithAddresses
            }
        }
        return liveData
    }

    fun resetPassword(email: String){
        val params  = JSONObject().also{
            it.put("email", email)
            it.put("requestType", "PASSWORD_RESET")
        }
        val request = JsonObjectRequest(Request.Method.POST,
            BASE_URL + PASSWORD_RESET + KEY,
            params,
            Response.Listener { response ->
                Log.d(this.toString(), response.keys().toString())
            },
            Response.ErrorListener { error ->
                Log.e(this.toString(), error.message ?: "Error")
            }
        )
        queue.add(request)
    }


    fun update(userWithAddresses: UserWithAddresses) : Boolean {
        var updated = false
        val userRef = firestore.collection("users").document(userWithAddresses.user.id)
        userRef.set(userWithAddresses.user).addOnSuccessListener { updated = true }

        val addressRef = userRef.collection("addresses")
        val addresses = userWithAddresses.addresses.first()

        if(addresses.id.isEmpty()){
            addressRef.add(addresses).addOnSuccessListener {
                addresses.id = it.id
                updated = true
            }
        } else {
            addressRef.document(addresses.id).set(addresses).addOnSuccessListener { updated = true }
        }

        return updated

    }

    fun uploadProfileImage(userID: String, photoUri: Uri): LiveData<String>{
        val liveData = MutableLiveData<String>()

        storege.reference.child("users/$userID/profile.jpg").putFile(photoUri).addOnSuccessListener {
            preference.edit().putString(MediaStore.EXTRA_OUTPUT, it.metadata?.path).apply() // guarda o caminho da foto armazenada no firebase
            liveData.value = it.metadata?.path // retorna o caminho da imagem como resposta da função
        }

        return liveData
    }

    fun loadImage(userID: String, imageView: ImageView) = storege.reference.child("users/$userID/profile.jpg")
        .downloadUrl.addOnSuccessListener {
            // disk chache, diz respeito a qual estratégia de manunetenção de dados em cahe será usada
            glide.load(it).diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(R.drawable.profile_image)
                .placeholder(R.drawable.profile_image)
                .into(imageView)
        }

    companion object {
        const val BASE_URL = "https://identitytoolkit.googleapis.com/v1/"
        const val SIGNUP = "accounts:signUp"
        const val SIGNIN = "accounts:signInWithPassword"
        const val PASSWORD_RESET = "accounts:sendOobCode"
        const val KEY = "?key=AIzaSyAHa4iiBp9Dj0nZ_3vXy-edDoX2DKu-VDY"
    }

}