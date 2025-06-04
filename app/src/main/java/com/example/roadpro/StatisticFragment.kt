package com.example.roadpro

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import android.widget.LinearLayout
import android.graphics.Color
import android.view.Gravity
import android.util.TypedValue

class StatisticFragment : Fragment() {

    private lateinit var monthsRecyclerView: RecyclerView
    private lateinit var adapter: MonthProfitAdapter
    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private lateinit var yearTextView: TextView
    private lateinit var prevYearButton: ImageButton
    private lateinit var nextYearButton: ImageButton
    private lateinit var barChartLayout: LinearLayout
    private lateinit var barChartXAxisLayout: LinearLayout
    private lateinit var barChartYAxisLayout: LinearLayout // dodaj to pole

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_statistic, container, false)
        monthsRecyclerView = view.findViewById(R.id.monthsRecyclerView)
        adapter = MonthProfitAdapter()
        monthsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        monthsRecyclerView.adapter = adapter

        yearTextView = view.findViewById(R.id.yearTextView)
        prevYearButton = view.findViewById(R.id.prevYearButton)
        nextYearButton = view.findViewById(R.id.nextYearButton)

        yearTextView.text = selectedYear.toString()

        prevYearButton.setOnClickListener {
            selectedYear--
            yearTextView.text = selectedYear.toString()
            loadProfitsForYear(selectedYear)
        }
        nextYearButton.setOnClickListener {
            selectedYear++
            yearTextView.text = selectedYear.toString()
            loadProfitsForYear(selectedYear)
        }

        // Dodaj wykres słupkowy pod RecyclerView
        barChartLayout = view.findViewById(R.id.barChartLayout)
        barChartXAxisLayout = view.findViewById(R.id.barChartXAxisLayout)
        barChartYAxisLayout = view.findViewById(R.id.barChartYAxisLayout) // znajdź layout osi Y

        loadProfitsForYear(selectedYear)
        return view
    }

    private fun loadProfitsForYear(year: Int) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("events")
            .whereEqualTo("userId", user.uid)
            .whereEqualTo("done", 1)
            .get()
            .addOnSuccessListener { result ->
                val monthProfits = DoubleArray(12) { 0.0 }
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                for (doc in result) {
                    val event = doc.toObject(Event::class.java)
                    val endDate = try { sdf.parse(event.endDate) } catch (e: Exception) { null }
                    if (endDate != null) {
                        val cal = Calendar.getInstance()
                        cal.time = endDate
                        val eventYear = cal.get(Calendar.YEAR)
                        val eventMonth = cal.get(Calendar.MONTH)
                        if (eventYear == year) {
                            val payment = event.payment ?: 0.0
                            val fees = event.fees?.sumOf { it.amount } ?: 0.0
                            val profit = payment - fees
                            monthProfits[eventMonth] += profit
                        }
                    }
                }
                val totalProfit = monthProfits.sum()
                val totalProfitForPercent = totalProfit.takeIf { it != 0.0 } ?: 1.0 // uniknij dzielenia przez 0
                val months = resources.getStringArray(R.array.months)
                val data = months.mapIndexed { idx, name ->
                    val percent = (monthProfits[idx] / totalProfitForPercent * 100.0)
                    MonthProfit(name, monthProfits[idx], percent)
                }
                adapter.submitList(data)
                // Ustaw sumaryczny zysk roczny na dole
                view?.findViewById<TextView>(R.id.yearTotalProfitTextView)?.text =
                    "Suma zysku: %.2f zł".format(totalProfit)

                // Dodaj wykres słupkowy
                drawBarChart(monthProfits)
            }
    }

    private fun drawBarChart(monthProfits: DoubleArray) {
        barChartLayout.removeAllViews()
        barChartXAxisLayout.removeAllViews()
        barChartYAxisLayout.removeAllViews() // czyść etykiety osi Y
        val maxProfit = monthProfits.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val barWidthDp = 18
        val barMarginDp = 4
        val maxBarHeightDp = 100

        // Dodaj dynamiczną oś Y (np. 5 poziomów)
        val ySteps = 5
        for (i in ySteps downTo 0) {
            val yValue = maxProfit * i / ySteps
            val label = TextView(requireContext())
            label.text = String.format("%.0f", yValue)
            label.setTextColor(Color.BLACK)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            label.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            val labelParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    (maxBarHeightDp / ySteps.toFloat()),
                    resources.displayMetrics
                ).toInt()
            )
            label.layoutParams = labelParams
            barChartYAxisLayout.addView(label)
        }

        for (i in 0..11) {
            val bar = View(requireContext())
            val barHeight = (monthProfits[i] / maxProfit * maxBarHeightDp).toInt()
            val params = LinearLayout.LayoutParams(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, barWidthDp.toFloat(), resources.displayMetrics).toInt(),
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, barHeight.toFloat(), resources.displayMetrics).toInt()
            )
            params.setMargins(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, barMarginDp.toFloat(), resources.displayMetrics).toInt(),
                0,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, barMarginDp.toFloat(), resources.displayMetrics).toInt(),
                0
            )
            bar.layoutParams = params
            bar.setBackgroundColor(Color.parseColor("#D0B6F6")) // pastelowy jasny fioletowy
            barChartLayout.addView(bar)

            // Dodaj podpis osi X (numer miesiąca)
            val label = TextView(requireContext())
            label.text = (i + 1).toString()
            label.gravity = Gravity.CENTER
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            val labelParams = LinearLayout.LayoutParams(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, barWidthDp.toFloat(), resources.displayMetrics).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            labelParams.setMargins(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, barMarginDp.toFloat(), resources.displayMetrics).toInt(),
                0,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, barMarginDp.toFloat(), resources.displayMetrics).toInt(),
                0
            )
            label.layoutParams = labelParams
            barChartXAxisLayout.addView(label)
        }
    }

    data class MonthProfit(val month: String, val profit: Double, val percent: Double)

    class MonthProfitAdapter : RecyclerView.Adapter<MonthProfitAdapter.ViewHolder>() {
        private var items: List<MonthProfit> = emptyList()
        fun submitList(list: List<MonthProfit>) {
            items = list
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_month_profit, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.monthName.text = item.month
            holder.monthProfit.text = String.format("%.2f zł (%.1f%%)", item.profit, item.percent)
            holder.monthName.setTextColor(Color.BLACK)
            holder.monthProfit.setTextColor(Color.BLACK)
        }
        override fun getItemCount() = items.size
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val monthName: TextView = view.findViewById(R.id.monthNameTextView)
            val monthProfit: TextView = view.findViewById(R.id.monthProfitTextView)
        }
    }
}

// ...Event class definition remains unchanged...