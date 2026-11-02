# STEP 1 — Контекст у Quest Book (видав, стіл, сундук)

## Мета
У книзі завдань гравець бачить:
- хто видав квест (ім'я власника столу)
- координати столу
- координати прив'язаних сундуків здачі

## Файли в цій папці
| Файл | Дія |
|------|-----|
| `quest/BookEntry.java` | **Замінити** свій BookEntry |
| `data/TableSnapshot.java` | **Новий** клас |
| `PATCH_QuestBookService.java` | **Замінити** QuestBookService (або злити collect) |
| `PATCH_QuestUi_tooltip.txt` | Фрагмент для QuestUi.tooltip |
| `PATCH_QuestBookScreen_card.txt` | Фрагмент для картки в книзі |
| `PATCH_QuestTableBlockEntity.txt` | Додати getContainerPositions + оновити pushToRegistry |
| `PATCH_QuestRegistry.txt` | Як змінити реєстр під TableSnapshot |
| `lang/uk_ua_add.json` / `en_us_add.json` | Дописати ключі в lang |

## Що змінити вручну в проєкті

### 1. BookEntry
Скопіюй `quest/BookEntry.java` замість старого.
Якщо `BookDataPacket` кодує entries вручну — переведи на `entry.encode(buf)` / `BookEntry.decode(buf)`.

### 2. QuestRegistry
Замість `Map<BoundContainer, List<Quest>>` тримай `Map<BoundContainer, TableSnapshot>`.

Методи приблизно такі:
```java
public void update(BoundContainer key, List<Quest> quests, String ownerName, List<BlockPos> chests) {
    map.put(key, new TableSnapshot(quests, ownerName, chests));
}

public Map<BoundContainer, TableSnapshot> allSnapshots() {
    return Collections.unmodifiableMap(map);
}

// якщо десь лишився all() зі списком квестів:
public Map<BoundContainer, List<Quest>> all() {
    Map<BoundContainer, List<Quest>> out = new HashMap<>();
    map.forEach((k, v) -> out.put(k, v.questsCopy()));
    return out;
}
```

### 3. QuestTableBlockEntity
Додай:
```java
public List<BlockPos> getContainerPositions() {
    List<BlockPos> list = new ArrayList<>(containers.size());
    for (BoundContainer c : containers) {
        list.add(c.pos());
    }
    return list;
}
```

І в `pushToRegistry()`:
```java
String ownerName = "";
if (owner != null && level.getServer() != null) {
    ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
    if (p != null) ownerName = p.getGameProfile().getName();
    else ownerName = level.getServer().getProfileCache().get(owner)
            .map(com.mojang.authlib.GameProfile::getName).orElse("");
}
QuestRegistry.get(level.getServer()).update(
        new BoundContainer(level.dimension(), worldPosition.immutable()),
        quests,
        ownerName,
        getContainerPositions());
```

Збережи також `ownerName` у snapshot при кожному markDirty — коли власник офлайн, останнє відоме ім'я лишається в реєстрі.

### 4. QuestUi.tooltip
Додай оверлоад або розшири сигнатуру:
```java
public static List<Component> tooltip(Quest q, BookEntry entry) { ... }
```
Див. `PATCH_QuestUi_tooltip.txt`.

### 5. QuestBookScreen
- У картці під описом (або замість частини опису) малюй короткий рядок: стіл + сундук.
- У tooltip передавай `entries.get(idx)` цілком, не лише quest.
Див. `PATCH_QuestBookScreen_card.txt`.

### 6. Lang
Допиши ключі з `lang/*_add.json` у свої `uk_ua.json` і `en_us.json`.

## Перевірка в грі
1. Постав стіл, створи квест, прив'яжи сундук.
2. Відкрий Quest Book.
3. На картці / в tooltip мають бути:
   - Видав: <нік>
   - Стіл: x, y, z
   - Сундук: x, y, z (або «Сундуків: N» / «Без сундука»)

## Далі (крок 2)
Авто / Ручна перевірка для ITEMS + відкат REVIEW, якщо забрали предмети з сундука.
