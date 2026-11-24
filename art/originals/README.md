# Original artwork

Full-size drawings from the artist, kept as the source of record. They are used in Snoozer with
the artist's permission.

| File | What it shows | Used in the app as |
|---|---|---|
| `bell.png` | The bell-hat guard with crossed arms (mascot) | `illus_guard` |
| `work.png` | The tired office worker with a laptop | `illus_work` |
| `study.png` | The character hauling a giant pencil | `illus_study` |

These files are outside `app/`, so they are not packaged into the app. The app uses 600×600
copies in `app/src/main/res/drawable-nodpi/`. At 2000×2000 these originals would take far too much
memory on a phone. To make a new in-app copy, resize from these files and keep the transparent
background.
