# Google Play listing and forms

Everything to paste or choose in Play Console for Snoozer 1.0.0. The answers follow from what
the app does today (no internet permission, no ads, no analytics, data on the phone only).
If the app changes, check them again.

## Store listing (Main store listing)

**App name** (30 max): `Snoozer: Quiet on Schedule` (26)

**Short description** (80 max):

```
Mutes your phone on schedule: vibrate for work, fully silent for study.
```

(71 characters)

**Full description** (4000 max):

```
Snoozer keeps your phone quiet when it should be, and turns the sound back on when you're done. You set the times once; Snoozer does the rest every week.

TWO KINDS OF QUIET
• Work: your phone vibrates instead of ringing, so calls and messages still reach you.
• Study: fully silent, no ring and no vibration. Alarms still ring.

PLAN YOUR WEEK
• Add a snoozer with a start time, an end time and the days it repeats.
• Or pick One date for a single day, like an exam or an appointment.
• Week and Month views show what's coming, and the card at the top tells you whether your phone is quiet now and until when.
• A heads-up 5 minutes before quiet time starts.

IMPORT YOUR TIMETABLE
Open a class schedule (.ics) from your school or calendar and Snoozer turns each class into a snoozer, with its name, days and term dates. Classes stop on their own when the term ends.

YOUR WEEKLY REPORT
Every Monday, see how many hours your phone stayed quiet last week, split into Work and Study, day by day, with your longest stretch and your quietest day.

RELIABLE
• Starts and ends on the minute, even when your phone is asleep.
• Sets your schedule again after a restart or a time zone change.
• A short Setup list shows which permissions it needs and why.

PRIVATE BY DESIGN
No account, no ads, no tracking. Snoozer has no internet access: your schedule and report never leave your phone.

Hand-drawn by an artist, with a light and a dark theme.
```

**Graphics**

| Asset | File | Play's rule |
|---|---|---|
| App icon | `docs/store/icon-512.png` | 512 × 512 PNG; Play rounds the corners |
| Feature graphic | `docs/store/feature-graphic.png` | 1024 × 500, no transparency |
| Phone screenshots | `docs/store/screenshots/01…06` | 2 to 8; these are 1080 × 1920 (9:16) |

Screenshots were taken on an emulator with sample snoozers, not a real phone's data.
`StoreArt.java` redraws the icon and feature graphic from `art/originals` and the app's font.

**Category:** Productivity. **Tags:** suggestions such as Productivity, Study, Time management.

**Contact details:** an email address is required and is shown publicly on the store page.
Use jakeMobileDev@gmail.com, the same address as in the privacy policy.

**Privacy policy URL:** the published address of `docs/privacy-policy.md` (see "Publishing
the privacy policy" below).

## Korean store listing (ko-KR)

In Play Console: Grow users → Store presence → Main store listing → Manage translations →
Add your own translations → Korean (한국어). Korean phones see this page; everyone else sees
the English one. Words follow the app's glossary: a snoozer is 스누저, Work is 일, Study is 공부,
and the app's name stays "Snoozer".

**App name** (30 max): `Snoozer: 일정대로 조용하게` (18)

**Short description** (80 max):

```
일할 땐 진동, 공부할 땐 완전 무음. 정해진 시간에 휴대폰을 알아서 조용하게 해요.
```

(47 characters)

**Full description** (4000 max):

```
Snoozer는 휴대폰이 조용해야 할 때 조용하게 하고, 끝나면 소리를 다시 켜 줘요. 시간은 한 번만 정하면 매주 Snoozer가 알아서 해요.

두 가지 조용함
• 일: 벨소리 대신 진동이 울려서 전화와 메시지는 그대로 받을 수 있어요.
• 공부: 벨소리도 진동도 없는 완전 무음. 알람은 그대로 울려요.

한 주 계획하기
• 시작 시간, 끝 시간, 반복할 요일을 정해 스누저를 추가하세요.
• 시험이나 약속처럼 하루만 필요하면 '하루만'을 고르세요.
• 주간·월간 보기로 앞으로의 일정을 보고, 맨 위 카드에서 지금 조용한지, 언제까지인지 바로 확인할 수 있어요.
• 스누저가 시작되기 5분 전에 미리 알려 줘요.

시간표 가져오기
학교나 캘린더의 수업 시간표(.ics)를 열면 수업마다 이름, 요일, 학기 기간이 담긴 스누저가 만들어져요. 학기가 끝나면 수업 스누저도 알아서 멈춰요.

주간 리포트
매주 월요일, 지난주에 휴대폰이 몇 시간 조용했는지 일과 공부로 나눠 요일별로 보여 줘요. 가장 긴 시간과 가장 조용한 날도 알려 줘요.

믿을 수 있게
• 휴대폰이 잠들어 있어도 정확히 그 분에 시작하고 끝나요.
• 휴대폰을 다시 켜거나 시간대가 바뀌어도 일정을 다시 맞춰요.
• 짧은 권한 설정 목록에서 어떤 권한이 왜 필요한지 보여 줘요.

개인정보는 휴대폰 안에만
계정도, 광고도, 추적도 없어요. Snoozer는 인터넷에 연결하지 않아서 일정과 리포트가 휴대폰 밖으로 나가지 않아요.

작가가 손으로 그린 그림과 밝은 테마, 어두운 테마.
```

(757 characters)

**Graphics:** under the Korean translation, add the phone screenshots from
`docs/store/screenshots/ko/01…06` (1080 × 1920, the same scenes as the English set). The icon and
feature graphic come from the English listing. The feature graphic's tagline ("Quiet on
schedule, for work and study.") stays in English on the Korean page unless a Korean graphic is
added; `StoreArt.java` could draw one.

## App content

### Data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| (Then Play skips the rest.) | |

Why "No": Play counts data as collected only when it's sent off the phone. Snoozer doesn't
declare the INTERNET permission, so it can't send anything. Android's own device backup is
run by Android, not the app. Read Play's current help text on backup when filling this in,
and answer from it if it says otherwise.

### Content rating (IARC questionnaire)

- Category: **All other app types** (or "Utility, productivity, communication, or other").
- Violence, sexuality, language, controlled substances, gambling: **No** to all.
- Do users interact or exchange content? **No.**
- Does the app share the user's location with other users? **No.**
- Digital purchases? **No.**
- Expected result: Everyone (ESRB), PEGI 3, and the matching ratings elsewhere.

### Other declarations

| Section | Answer |
|---|---|
| Ads | **No**, the app has no ads |
| App access | **All functionality is available without special access** (no login) |
| Target audience | **13 and over** (13–15, 16–17, 18+). Choosing under 13 brings in the Families policy. |
| News app | No |
| COVID-19 contact tracing or status | No |
| Government app | No |
| Financial features | None |
| Health | No health features |
| Exact alarms | Uses `SCHEDULE_EXACT_ALARM`, which people grant in settings. It is not `USE_EXACT_ALARM`, so no extra declaration is expected. |
| Foreground service permissions | **Yes**, type **Special use** (`FOREGROUND_SERVICE_SPECIAL_USE`). Text below. |

### Foreground service declaration (from 1.0.1)

App content → Foreground service permissions → Special use. Play asks what the service does, why
it must run in the foreground, and what users would lose if it were deferred. It may also ask for
a short video.

**Description**

```
At the start and end of a quiet time the user has scheduled in the app, Snoozer switches the phone's ringer to vibrate or silent, and later back to the user's own mode. Each switch runs in a foreground service for about one second and then stops. Android 17 ignores ringer changes (AudioManager.setRingerMode) from apps without a visible screen or a foreground service, so without it the phone would not go quiet when the app is closed.
```

**Impact if deferred or interrupted**

```
The phone would keep ringing during the quiet time the user scheduled (a class, an exam, work), or stay silent after it ends so the user misses calls. The change must happen at the scheduled minute.
```

**Video (if asked):** a screen recording of adding a snoozer that starts in two minutes, closing
Snoozer from Recents, and the ringer switching at the start time.

## Publishing the privacy policy

The policy is `docs/privacy-policy.md`. To publish it with GitHub Pages:

1. Merge it into `main` (its contact address is already filled in).
2. In the GitHub repository: Settings → Pages → Source: Deploy from a branch → `main`, folder
   `/docs`.
3. The policy will be at `https://<github-user>.github.io/<repository>/privacy-policy`.
   Paste that address into Play Console.

GitHub Pages serves everything in `docs/`, including the design mocks and these notes, and
needs a public repository on a free plan.
