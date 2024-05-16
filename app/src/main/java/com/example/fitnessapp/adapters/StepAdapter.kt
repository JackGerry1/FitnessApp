import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fitnessapp.R
import com.example.fitnessapp.constants.StepsData
import java.text.SimpleDateFormat
import java.util.Locale

/*
References:

  CodingTutorials (2022). Recycler View in Android Studio | Populate Recycler View with Firebase Database (with Source Code). [online]
  Youtube. Available at: https://www.youtube.com/watch?v=p2KmuAO8YsE [Accessed 8 May 2024].

*/

class StepAdapter(private val stepsDataList: List<StepsData>) :
    RecyclerView.Adapter<StepAdapter.StepViewHolder>() {
    // where the data for the steps will be displayed
    inner class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateTextView: TextView = itemView.findViewById(R.id.textDateSteps)
        val stepsTextView: TextView = itemView.findViewById(R.id.textSteps)
        val distanceTextView: TextView = itemView.findViewById(R.id.textStepDistance)
        val caloriesTextView: TextView = itemView.findViewById(R.id.textStepCaloriesBurned)
        val goalReachedTextView: TextView = itemView.findViewById(R.id.textStepGoalStatus)
    }
    // display the step data
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_steps, parent, false)
        return StepViewHolder(itemView)
    }

    // set all of the data for the steps
    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {

        // get current date and step data
        val stepsData = stepsDataList[position]
        val currentDate = stepsData.currentDate

        // format date into 07/05/2024
        val inputFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val date = inputFormat.parse(currentDate!!)
        val formattedCurrentDate = outputFormat.format(date!!)

        // display the step stats into the recycler view
        holder.dateTextView.text = "${formattedCurrentDate ?: "N/A"}"
        holder.stepsTextView.text = "${stepsData.steps ?: "N/A"}"
        holder.distanceTextView.text = "${stepsData.distanceInKM ?: "N/A"} KM"
        holder.caloriesTextView.text = "${stepsData.caloriesBurned ?: "N/A"}"
        holder.goalReachedTextView.text = "${stepsData.goalReached ?: "N/A"}"

    }
    // iterate through all of the data
    override fun getItemCount(): Int {
        return stepsDataList.size
    }
}
