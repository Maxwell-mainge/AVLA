package com.avla.app.utils

import com.avla.app.data.model.AppUser
import com.avla.app.data.model.Listing
import com.avla.app.data.model.PropertyType
import com.avla.app.data.model.UserRole
import com.avla.app.data.model.VerificationStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * DataSeeder — populates Firestore with realistic Kenyan campus housing mock data.
 * Only runs in DEBUG builds and only once (checks a sentinel document first).
 * Call seedIfNeeded() from MainActivity inside a coroutine.
 */
object DataSeeder {

    private const val SEEDER_FLAG = "meta/seeded_v1"

    private val APARTMENT_IMAGES = listOf(
        listOf(
            "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=800",
            "https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?w=800",
            "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?w=800"
        ),
        listOf(
            "https://images.unsplash.com/photo-1484154218962-a197022b5858?w=800",
            "https://images.unsplash.com/photo-1493809842364-78817add7ffb?w=800",
            "https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=800"
        ),
        listOf(
            "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?w=800",
            "https://images.unsplash.com/photo-1507089947368-19c1da9775ae?w=800",
            "https://images.unsplash.com/photo-1586023492125-27b2c045efd7?w=800"
        ),
        listOf(
            "https://images.unsplash.com/photo-1615873968403-89e068629265?w=800",
            "https://images.unsplash.com/photo-1631049307264-da0ec9d70304?w=800"
        ),
        listOf(
            "https://images.unsplash.com/photo-1560185007-cde436f6a4d0?w=800",
            "https://images.unsplash.com/photo-1564013799919-ab600027ffc6?w=800",
            "https://images.unsplash.com/photo-1598928506311-c55ded91a20c?w=800"
        )
    )

    private val mockLandlords = listOf(
        AppUser(
            uid = "landlord_001", fullName = "James Kamau",
            email = "james.kamau@test.com", phone = "0712345001",
            nationalId = "12345001", role = UserRole.LANDLORD,
            operatingLocation = "Kahawa West",
            verificationStatus = VerificationStatus.VERIFIED
        ),
        AppUser(
            uid = "landlord_002", fullName = "Grace Wanjiku",
            email = "grace.wanjiku@test.com", phone = "0712345002",
            nationalId = "12345002", role = UserRole.LANDLORD,
            operatingLocation = "Ruiru",
            verificationStatus = VerificationStatus.VERIFIED
        ),
        AppUser(
            uid = "landlord_003", fullName = "Peter Otieno",
            email = "peter.otieno@test.com", phone = "0712345003",
            nationalId = "12345003", role = UserRole.LANDLORD,
            operatingLocation = "South C",
            verificationStatus = VerificationStatus.VERIFIED
        ),
        AppUser(
            uid = "landlord_004", fullName = "Mary Njeri",
            email = "mary.njeri@test.com", phone = "0712345004",
            nationalId = "12345004", role = UserRole.LANDLORD,
            operatingLocation = "Kasarani",
            verificationStatus = VerificationStatus.VERIFIED
        ),
        AppUser(
            uid = "landlord_005", fullName = "Samuel Kipchoge",
            email = "samuel.kip@test.com", phone = "0712345005",
            nationalId = "12345005", role = UserRole.LANDLORD,
            operatingLocation = "Ngong Road",
            verificationStatus = VerificationStatus.VERIFIED
        )
    )

    private val mockStudents = listOf(
        AppUser(
            uid = "student_001", fullName = "Brian Mwangi",
            email = "brian.mwangi@student.test", phone = "0798001001",
            nationalId = "34500001", role = UserRole.STUDENT, campus = "CUEA"
        ),
        AppUser(
            uid = "student_002", fullName = "Aisha Hassan",
            email = "aisha.hassan@student.test", phone = "0798001002",
            nationalId = "34500002", role = UserRole.STUDENT, campus = "Strathmore University"
        ),
        AppUser(
            uid = "student_003", fullName = "Kevin Ochieng",
            email = "kevin.ochieng@student.test", phone = "0798001003",
            nationalId = "34500003", role = UserRole.STUDENT, campus = "UoN Main Campus"
        )
    )

    @Suppress("SpellCheckingInspection")
    private fun mockListings() = listOf(
        Listing(
            id = "listing_001", landlordUid = "landlord_001",
            landlordName = "James Kamau", landlordPhone = "0712345001",
            title = "Modern Bedsitter Near CUEA",
            description = "Spacious bedsitter on 3rd floor with large windows and natural light. " +
                    "Tiled floors, clean shared bathrooms (1 per 4 units), running water 24hrs. " +
                    "5 minutes walk from CUEA main gate. Caretaker on site. " +
                    "No pets. Single occupancy preferred. Call James on 0712345001.",
            propertyType = PropertyType.BEDSITTER, priceKsh = 8_500, bedrooms = 1,
            location = "Kahawa West", nearCampus = "CUEA", distanceKm = 0.5,
            amenities = listOf("Running Water", "Security", "Parking", "WIFI Ready"),
            imageUrls = APARTMENT_IMAGES[0]
        ),
        Listing(
            id = "listing_002", landlordUid = "landlord_001",
            landlordName = "James Kamau", landlordPhone = "0712345001",
            title = "1 Bedroom — Kahawa West, Near CUEA",
            description = "Self-contained 1 bedroom unit. Own bathroom and kitchen. " +
                    "Tiled, fitted with burglar-proof windows. " +
                    "10 mins walk to CUEA. Borehole water available. " +
                    "Electricity prepaid. Contact James: 0712345001.",
            propertyType = PropertyType.ONE_BEDROOM, priceKsh = 14_000, bedrooms = 1,
            location = "Kahawa West", nearCampus = "CUEA", distanceKm = 0.8,
            amenities = listOf("Running Water", "Security Guard", "Own Bathroom", "Prepaid Electricity"),
            imageUrls = APARTMENT_IMAGES[1]
        ),
        Listing(
            id = "listing_003", landlordUid = "landlord_002",
            landlordName = "Grace Wanjiku", landlordPhone = "0712345002",
            title = "Studio Apartment — Madaraka, Near Strathmore",
            description = "Cosy studio apartment fully furnished (bed, wardrobe, sofa). " +
                    "Own bathroom, kitchenette with gas cooker. " +
                    "Gated community with CCTV, 3 mins to Strathmore University. " +
                    "Ideal for students. Contact Grace: 0712345002.",
            propertyType = PropertyType.STUDIO, priceKsh = 18_000, bedrooms = 1,
            location = "Madaraka", nearCampus = "Strathmore University", distanceKm = 0.3,
            amenities = listOf("Furnished", "CCTV", "Own Bathroom", "Gas Cooker", "WiFi"),
            imageUrls = APARTMENT_IMAGES[2]
        ),
        Listing(
            id = "listing_004", landlordUid = "landlord_003",
            landlordName = "Peter Otieno", landlordPhone = "0712345003",
            title = "2 Bedroom Flat — South C, Near UoN",
            description = "Spacious 2 bedroom flat, suitable for 2 students sharing. " +
                    "Large living room, fitted kitchen, 2 bathrooms. " +
                    "20 mins matatu to UoN Main Campus. Secure parking for 1 car. " +
                    "Water included in rent. Call Peter: 0712345003.",
            propertyType = PropertyType.TWO_BEDROOM, priceKsh = 28_000, bedrooms = 2,
            location = "South C", nearCampus = "UoN Main Campus", distanceKm = 3.2,
            amenities = listOf("Running Water", "Parking", "2 Bathrooms", "Kitchen", "Security"),
            imageUrls = APARTMENT_IMAGES[3]
        ),
        Listing(
            id = "listing_005", landlordUid = "landlord_004",
            landlordName = "Mary Njeri", landlordPhone = "0712345004",
            title = "Bedsitter — Kasarani, Near USIU",
            description = "Clean bedsitter in a quiet block. Shared kitchen (2 units per kitchen). " +
                    "Running water, electricity on token. " +
                    "5 mins walk from USIU Africa gate. Caretaker lives on-site. " +
                    "No parties. Contact Mary: 0712345004.",
            propertyType = PropertyType.BEDSITTER, priceKsh = 7_000, bedrooms = 1,
            location = "Kasarani", nearCampus = "USIU Africa", distanceKm = 0.5,
            amenities = listOf("Running Water", "Shared Kitchen", "Security"),
            imageUrls = APARTMENT_IMAGES[4]
        ),
        Listing(
            id = "listing_006", landlordUid = "landlord_004",
            landlordName = "Mary Njeri", landlordPhone = "0712345004",
            title = "1 Bedroom — Kasarani, USIU Road",
            description = "Brand new 1 bedroom unit, never occupied. " +
                    "Own bathroom and shower, open-plan kitchen and living room. " +
                    "Painted white walls, LED lighting throughout. " +
                    "8 mins walk to USIU main gate. Contact Mary: 0712345004.",
            propertyType = PropertyType.ONE_BEDROOM, priceKsh = 16_500, bedrooms = 1,
            location = "Kasarani", nearCampus = "USIU Africa", distanceKm = 0.8,
            amenities = listOf("Running Water", "Own Bathroom", "LED Lighting", "Security"),
            imageUrls = APARTMENT_IMAGES[0]
        ),
        Listing(
            id = "listing_007", landlordUid = "landlord_005",
            landlordName = "Samuel Kipchoge", landlordPhone = "0712345005",
            title = "3 Bedroom — Ngong Road, Near Strathmore",
            description = "Large 3 bedroom apartment suitable for 3 students. " +
                    "Each room has its own lock. Shared sitting room and kitchen. " +
                    "2 bathrooms. 15 mins walk to Strathmore. " +
                    "Rent 32k per month all inclusive (water and garbage). " +
                    "Contact Samuel: 0712345005.",
            propertyType = PropertyType.THREE_BEDROOM, priceKsh = 32_000, bedrooms = 3,
            location = "Ngong Road", nearCampus = "Strathmore University", distanceKm = 1.5,
            amenities = listOf("Water Included", "Garbage Collection", "2 Bathrooms", "Security", "Parking"),
            imageUrls = APARTMENT_IMAGES[1]
        ),
        Listing(
            id = "listing_008", landlordUid = "landlord_002",
            landlordName = "Grace Wanjiku", landlordPhone = "0712345002",
            title = "Bedsitter — Ruiru, Near KCA University",
            description = "Affordable bedsitter in Ruiru town. " +
                    "10 mins walk from KCA University Ruiru Campus. " +
                    "Shared bathrooms, clean compound, caretaker available. " +
                    "Token electricity. Ideal for first-year students. " +
                    "Call Grace: 0712345002.",
            propertyType = PropertyType.BEDSITTER, priceKsh = 6_000, bedrooms = 1,
            location = "Ruiru", nearCampus = "KCA University", distanceKm = 1.0,
            amenities = listOf("Running Water", "Security", "Caretaker"),
            imageUrls = APARTMENT_IMAGES[2]
        ),
        Listing(
            id = "listing_009", landlordUid = "landlord_003",
            landlordName = "Peter Otieno", landlordPhone = "0712345003",
            title = "2 Bedroom — Kileleshwa, Near USIU",
            description = "Upmarket 2 bedroom apartment in Kileleshwa. " +
                    "Fully tiled, fitted kitchen with electric cooker and oven. " +
                    "Backup generator, borehole water, gym in compound. " +
                    "20 mins to USIU. Ideal for 2 working students. " +
                    "Contact Peter: 0712345003.",
            propertyType = PropertyType.TWO_BEDROOM, priceKsh = 45_000, bedrooms = 2,
            location = "Kileleshwa", nearCampus = "USIU Africa", distanceKm = 4.0,
            amenities = listOf("Generator Backup", "Borehole Water", "Gym", "Fitted Kitchen", "Security"),
            imageUrls = APARTMENT_IMAGES[3]
        ),
        Listing(
            id = "listing_010", landlordUid = "landlord_005",
            landlordName = "Samuel Kipchoge", landlordPhone = "0712345005",
            title = "Studio — Adams Arcade, Near Strathmore",
            description = "Compact studio apartment perfect for a solo student. " +
                    "Kitchenette, own bathroom with hot shower. " +
                    "5 mins drive or 20 mins walk to Strathmore. " +
                    "Quiet compound, no loud music policy enforced. " +
                    "Available immediately. Contact Samuel: 0712345005.",
            propertyType = PropertyType.STUDIO, priceKsh = 22_000, bedrooms = 1,
            location = "Adams Arcade", nearCampus = "Strathmore University", distanceKm = 2.0,
            amenities = listOf("Hot Shower", "Own Bathroom", "Kitchenette", "WiFi Ready", "Security"),
            imageUrls = APARTMENT_IMAGES[4]
        )
    )

    // Update your function signature to accept the active user's ID
    // 1. Update the function signature to take the active user's UID
    suspend fun seedIfNeeded() {
        val db = FirebaseFirestore.getInstance()
        val flagRef = db.document(SEEDER_FLAG)
        if (flagRef.get().await().exists()) return

        val listingsCol = db.collection("listings")
        val usersCol = db.collection("users")

        // 1. Upload mock landlords
        for (user in mockLandlords) {
            usersCol.document(user.uid).set(user).await()
        }

        // 2. Upload mock students
        for (user in mockStudents) {
            usersCol.document(user.uid).set(user).await()
        }

        // 3. Upload original unmutated mock listings matching "landlord_001"
        for (listing in mockListings()) {
            listingsCol.document(listing.id).set(listing).await()
        }

        flagRef.set(mapOf("seeded" to true)).await()
    }


}