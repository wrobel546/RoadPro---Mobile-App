import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.roadpro.R

data class GasStationWithDistance(
    val name: String,
    val lat: Double,
    val lng: Double,
    val distanceKm: Double
)

class GasStationAdapter(
    private val stations: List<GasStationWithDistance>
) : RecyclerView.Adapter<GasStationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.stationNameText)
        val distanceText: TextView = view.findViewById(R.id.stationDistanceText)
        val navigateBtn: Button = view.findViewById(R.id.navigateBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gas_station, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val station = stations[position]
        holder.nameText.text = station.name
        holder.distanceText.text = "Odległość: %.1f km".format(station.distanceKm)
        holder.navigateBtn.setOnClickListener {
            val uri = Uri.parse("google.navigation:q=${station.lat},${station.lng}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount() = stations.size
}
