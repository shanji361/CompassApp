# Compass & Digital Level App
## Introduction

This app demonstrates a compass and a digital level using Android sensors.
--- 
### Compass:

- Uses the magnetometer and accelerometer to calculate the heading.

- Displays a rotating compass needle with cardinal directions and minor/major markers on a circular Canvas.

### Digital Level:

- Uses the gyroscope (fused with accelerometer data) to measure pitch and roll.

- Shows a visual level with numerical angles and an indicator for whether the device is LEVEL or TILTED.

- The app applies sensor fusion using a complementary filter to combine gyroscope and accelerometer data for smoother and more accurate pitch and roll readings.

## Features 

- Real-time compass heading with needle rotation.

- Digital level for pitch and roll.

- Level indicator that changes color and status based on orientation.

- Major and minor markers on compass for readability.
--- 
## How to Run
1. Clone this repository:
   ```
   git clone https://github.com/shanji361/CompassApp.git
   ```
2. Open the project in Android Studio.

3. Run the app on an emulator or a physical Android device.
   
--- 
## Reference 
- Compass Degree Markers: Used AI to design the logic for drawing compass degree markers around the circle, including differentiating between major markers (every 30°) and minor markers (every 10°).
- Cardinal Direction Mapping: Used AI to create the function that converts the azimuth (0–360° heading) into human-readable compass directions like N, NE, E, etc., based on specific degree ranges.
- Complementary Filter Logic: Used AI to help design the logic that fuses gyroscope and accelerometer data for accurate pitch and roll calculation.
