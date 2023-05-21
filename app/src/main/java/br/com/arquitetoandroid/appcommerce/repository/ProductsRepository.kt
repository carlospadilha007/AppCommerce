package br.com.arquitetoandroid.appcommerce.repository

import android.app.Application
import android.widget.ImageView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import br.com.arquitetoandroid.appcommerce.R
import br.com.arquitetoandroid.appcommerce.database.AppDatabase
import br.com.arquitetoandroid.appcommerce.model.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.storage.ktx.storage

class ProductsRepository (application: Application) {

    private val productDao = AppDatabase.getDatabase(application).productDao()

    private val productCategoryDao = AppDatabase.getDatabase(application).productCategoryDao()

    private val firestore = FirebaseFirestore.getInstance()

    private val storage = Firebase.storage(Firebase.app)

    private val glide = Glide.with(application)

    fun allCategories(): LiveData<List<ProductCategory>>{
        val liveData = MutableLiveData<List<ProductCategory>>()

        //o método snapshot atualiza os dados automáticamente
        firestore.collection("product_categories").addSnapshotListener { snap, exception ->
            if (exception != null) return@addSnapshotListener

            val list = mutableListOf<ProductCategory>()

            snap?.forEach {
                val productCategory = it.toObject(ProductCategory::class.java)
                productCategory.id = it.id
                list.add(productCategory)
            }
            liveData.value = list

        }
        return liveData
    }

    fun featuredCategories() : LiveData<List<ProductCategory>>{
        val liveData = MutableLiveData<List<ProductCategory>>()

        //o método snapshot atualiza os dados automáticamente
        firestore.collection("product_categories")
            .whereEqualTo("featured", true) // adiciona um filtro para um atributo
            .addSnapshotListener { snap, exception ->
            if (exception != null) return@addSnapshotListener

            val list = mutableListOf<ProductCategory>()

            snap?.forEach {
                val productCategory = it.toObject(ProductCategory::class.java)
                productCategory.id = it.id
                list.add(productCategory)
            }
            liveData.value = list

        }
        return liveData
    }

    fun featuredProducts() : LiveData<List<Product>>{
        val liveData = MutableLiveData<List<Product>>()

        //o método snapshot atualiza os dados automáticamente
        firestore.collection("products")
            .whereEqualTo("featured", true) // adiciona um filtro para um atributo
            .addSnapshotListener { snap, exception ->
                if (exception != null) return@addSnapshotListener

                val list = mutableListOf<Product>()

                snap?.forEach {
                    val product = it.toObject(Product::class.java)
                    product.id = it.id
                    list.add(product)
                }
                liveData.value = list

            }
        return liveData
    }

    fun loadProductsByCategory(categoryId: String) : LiveData<List<Product>>{
        val liveData = MutableLiveData<List<Product>>()
        firestore.collection("product_categories")
            .document(categoryId)
            .get(Source.CACHE)
            .addOnSuccessListener { category->
                val list = mutableListOf<Product>()
                if (category.get("products") != null){
                    val productRef = category.get("products") as List<DocumentReference>
                    productRef.forEach { doc->
                        firestore.document(doc.path).get().addOnSuccessListener { p->
                            val product = p.toObject(Product::class.java)
                            product?.id = p.id
                            list.add(product!!)
                            liveData.value = list
                        }
                    }
                }
            }
        return liveData
    }

    fun loadProductById(productId: String) : LiveData<ProductVariants>{
        val productVariants =  ProductVariants()
        val liveData = MutableLiveData<ProductVariants>(productVariants)

        val productRef = firestore.collection("products").document(productId)
        productRef.get().addOnSuccessListener { snap->
            productVariants.apply {
                product = snap?.toObject(Product::class.java)!!
                product.id = snap.id
                liveData.value = this
            }
        }
        productRef.collection("colors").get().addOnSuccessListener { colors->
           productVariants.colors = colors.toObjects(ProductColor::class.java)
           liveData.value = productVariants
        }
        productRef.collection("sizes").get().addOnSuccessListener { sizes->
            productVariants.sizes = sizes.toObjects(ProductSize::class.java)
            liveData.value = productVariants
        }
        productRef.collection("images").get().addOnSuccessListener { images->
            productVariants.images = images.toObjects(ProductImage::class.java)
            liveData.value = productVariants
        }
        return liveData
    }

    fun loadThumbnail(product: Product, imageView: ImageView){
        storage.reference.child("products/${product.id}/${product.thumbnail}")
            .downloadUrl.addOnSuccessListener {
                glide.load(it).diskCacheStrategy(DiskCacheStrategy.ALL)
                    .error(R.drawable.placeholder)
                    .placeholder(R.drawable.placeholder)
                    .into(imageView)
            }
    }

    fun loadImages(product: Product, path: String, imageView: ImageView){
        storage.reference.child("products/${product.id}/${path}")
            .downloadUrl.addOnSuccessListener {
                glide.load(it).diskCacheStrategy(DiskCacheStrategy.ALL)
                    .error(R.drawable.placeholder)
                    .placeholder(R.drawable.placeholder)
                    .into(imageView)
            }
    }

}