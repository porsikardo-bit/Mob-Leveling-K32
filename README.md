# Mob Leveling-K32

Mod para **Minecraft 1.20.1 (Forge)** que hace crecer la dificultad del mundo: los monstruos hostiles suben de nivel según la **zona** en la que aparecen y los **días** transcurridos.

> *English summary: a Forge 1.20.1 mod that levels up hostile mobs by distance zones and world days (+10% health and damage per level, max level 100). Boss mobs are left untouched by default. Server-side logic, works in singleplayer, LAN and dedicated servers.*

## Características

- **Zonas:** cada 1200 bloques desde el spawn se entra a una nueva zona. Al entrar o volver a una zona aparece un aviso en pantalla (Zona 1, 2, 3…).
- **Nivel base por zona:** +3 niveles por zona, desde el día 1.
- **Días:** el día 3 suena un aviso y los monstruos empiezan a subir de nivel (+1 cada 5 días).
- **Niveles aleatorios:** algunos mobs pueden nacer con hasta +5 niveles extra (cada nivel extra es menos probable).
- **Escalado:** +10% de vida y daño por nivel, hasta el nivel 100.
- **Nivel visible:** el nivel aparece en el nombre del mob (`[Nv. 12] Zombie`).
- **Idiomas:** inglés y español. Para agregar otro idioma, crea un archivo (por ejemplo `fr_fr.json`) en `src/main/resources/assets/mob_leveling/lang/` con las mismas claves que `es_es.json`.
- **Compatible con otros mods:** afecta a cualquier mob hostil. Los jefes (vanilla o de otros mods) no se modifican por defecto.

Fórmula: `nivel = 1 + (zona × 3) + bonus_por_días + bonus_aleatorio` (máx. 100)

## Requisitos

- Minecraft 1.20.1
- Forge 47.x
- Java 17

## Instalación

Descarga el `.jar` desde la sección **Releases** y colócalo en la carpeta `mods`.

## Multijugador

Toda la lógica corre en el servidor (o en el servidor integrado del mundo LAN). Si juegas con amigos, lo más seguro es que **todos tengan el mod instalado**.

## Configuración

Por ahora los valores están como constantes al inicio de
`src/main/java/net/mcreator/mobleveling/MobLevelingHandler.java`
(tamaño de zona, niveles por zona, día de aviso, días por nivel, nivel máximo, porcentaje, probabilidad aleatoria, protección de jefes y lista de exclusiones).

## Compilar

Proyecto creado con [MCreator](https://mcreator.net). Para compilar desde la terminal:

```
./gradlew build
```

El `.jar` queda en `build/libs/`. También puedes abrir el archivo `.mcreator` con MCreator.

## Contribuir

Los issues y pull requests son bienvenidos.

## Licencia

Pendiente de definir (agrega aquí la licencia que elijas y un archivo `LICENSE`).
