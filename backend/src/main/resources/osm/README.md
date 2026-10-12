# Datos de calles de San Miguel, Puno

`san-miguel-puno-roads.json` es un extract local y simplificado de vías de OpenStreetMap. Se consultó una sola vez a Overpass API el 9 de octubre de 2026, para el cuadro `[-15.52, -70.18, -15.36, -70.05]`, que cubre San Miguel y sectores cercanos de Juliaca. El snapshot de OSM informa `2026-10-09T14:21:20Z`. El archivo contiene 5,644 vías (1,999 con nombre) y ocupa aproximadamente 724 KB.

Los datos de OpenStreetMap se distribuyen bajo Open Database License (ODbL) 1.0. Este extract derivado también se ofrece bajo ODbL 1.0. Atribución visible requerida: **© OpenStreetMap contributors**. Licencia: https://www.openstreetmap.org/copyright

El archivo HTML descargable toma de este recurso únicamente las líneas que intersectan el entorno acotado de las visitas. Las visitas que queden fuera del extracto mantienen sus coordenadas reales en un panel geográfico separado, sin calles ficticias ni una escala que comprima el mapa principal. No solicita teselas ni llama a Overpass al descargarse o abrirse.

Para actualizar manualmente el extract, ejecuta `python3 backend/scripts/update-osm-road-data.py`. El script hace una única consulta acotada y reemplaza el snapshot local solo si obtiene geometrías utilizables. También permite convertir una respuesta ya descargada con `--input archivo.json`.
