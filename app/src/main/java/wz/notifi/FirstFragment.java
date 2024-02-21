package wz.notifi;


import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import wz.notifi.databinding.FragmentFirstBinding;

public class FirstFragment extends Fragment implements SpeedometerUtil.OnSpeedChangeListener {


    private FragmentFirstBinding binding;

    private SpeedometerUtil speedometerUtil;


    @Override
    public void onSpeedChanged(float speed) {
        // Do something with the speed in the fragment
        Log.d("Speedometer", "Current speed: " + speed + " km/h");
        binding.speedText.setText(speed+" km/h");
    }


    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 测试switch
        binding.switchSpeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean testSpeedState = binding.switchSpeed.isChecked();

                testSpeed(testSpeedState);
                Toast.makeText(getContext(), "GPS测速: " + (testSpeedState ? "开启" : "关闭"), Toast.LENGTH_SHORT).show();
            }
        });
        // 播报switch
        binding.readSpeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean readSpeedState = binding.readSpeed.isChecked();
                readSpeed(readSpeedState);
                Toast.makeText(getContext(), "播报: " + (readSpeedState ? "开启" : "关闭"), Toast.LENGTH_SHORT).show();
            }
        });

    }


    // 测速
    public void testSpeed(boolean isOepn) {
        if (isOepn) {
            binding.speedText.setText("开始");

            speedometerUtil = new SpeedometerUtil(requireContext());
            speedometerUtil.setOnSpeedChangeListener(this);
            speedometerUtil.startListeningForSpeedUpdates();
        } else {
            binding.speedText.setText("结束");
            speedometerUtil.stopListeningForSpeedUpdates();
        }
    }

    // 播报
    public void readSpeed(boolean isOepn) {
        if (isOepn) {
            binding.speedText.setText("开始");
        } else {
            binding.speedText.setText("结束");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        speedometerUtil.stopListeningForSpeedUpdates();
    }

}