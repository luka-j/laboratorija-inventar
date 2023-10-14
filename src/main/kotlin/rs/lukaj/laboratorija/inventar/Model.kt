package rs.lukaj.laboratorija.inventar

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name="items", schema = "inventar4")
class Item(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(columnDefinition = "serial")
        var id : Int,
        var ime : String,
        var dobavljac : String,
        var brPartije : Int,
        var brStavke : Int,
        var cena : Double,
        var rgn : String?,
        @OneToMany(mappedBy = "item", cascade = [CascadeType.ALL], orphanRemoval = true)
        var inventory : List<InventoryItem>
) {
        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Item

                if (brPartije != other.brPartije) return false
                if (brStavke != other.brStavke) return false

                return true
        }

        override fun hashCode(): Int {
                var result = brPartije
                result = 31 * result + brStavke
                return result
        }
}

@Entity
@Table(name = "inventories", schema = "inventar4",
        uniqueConstraints = [UniqueConstraint(name = "inventories_ime_uindex", columnNames = ["ime"])])
class Inventory(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(columnDefinition = "serial")
        var id : Int,
        var ime : String,
        @OneToMany(mappedBy = "inventory", cascade = [CascadeType.ALL], orphanRemoval = true)
        var items : List<InventoryItem>,
        var sortOrder : Int,
        var modifiable : Boolean
)

@Entity
@Table(name = "inventory_items", schema = "inventar4")
class InventoryItem(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(columnDefinition = "serial")
        var id : Long,
        @ManyToOne
        var inventory : Inventory,
        @ManyToOne
        var item : Item,
        var kolicina : Double,
        @OneToMany(mappedBy = "item", cascade = [CascadeType.ALL], orphanRemoval = true)
        var changes : List<Change>
) {
        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as InventoryItem

                return id == other.id
        }

        override fun hashCode(): Int {
                return id.hashCode()
        }
}

enum class ChangeType {
        ORDINARY, REVERSAL, EXPIRATION
}

@Entity
@Table(name = "changes", schema = "inventar4")
class Change(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(columnDefinition = "serial")
        var id: Long,
        @ManyToOne
        var item: InventoryItem,
        var amount: Double,
        var date: LocalDateTime,
        var expirationTime: LocalDateTime?,
        @Enumerated(EnumType.STRING)
        var type: ChangeType = ChangeType.ORDINARY
)