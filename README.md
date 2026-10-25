# TemperatureMap Android

Android-приложение с картой OpenStreetMap и текущей температурой в нескольких точках. Температурные данные берутся из бесплатного API Open-Meteo.

## Возможности
- Карта OpenStreetMap с цветными температурными метками.
- Цвет метки зависит от температуры.
- Кнопки обновления данных и перехода к Москве.
- GitHub Actions автоматически собирает debug APK и публикует его как artifact.

## Сборка локально
Требуется JDK 17 и Android SDK 36:

```sh
gradle :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions
После push откройте вкладку **Actions**, выберите завершённый запуск и скачайте artifact **TemperatureMap-debug-apk**.

Карта использует OpenStreetMap; погода предоставляется Open-Meteo. Данные отображаются для демонстрационной сетки координат.
