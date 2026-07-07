# OpenMW-DS Controller Scheme

## General principles
- Controller input is intercepted when companion DS overlays are active
- Native OpenMW handles controller input during normal gameplay
- B always closes/cancels (consistent with native Morrowind)
- Controls are the same regardless of Bottom/Split/Top mode
- Right stick scrolls wherever scrolling is available

---

## Conversation (all modes)

| Input | Action |
|-------|--------|
| D-pad up/down | Navigate topics list |
| A | Select highlighted topic |
| B | Goodbye / close conversation |
| Right stick up/down | Scroll conversation history |

---

## Looting / Pickpocketing (all modes)

| Input | Action |
|-------|--------|
| D-pad up/down/left/right | Navigate icon grid |
| L2 / R2 | Switch between player and container column |
| A | Take/put selected item |
| X | Take All |
| R1 | Dispose of Corpse (looting only) |
| B | Close |

---

## Bartering (all modes)

| Input | Action |
|-------|--------|
| D-pad up/down/left/right | Navigate icon grid |
| L2 / R2 | Switch between player and vendor column |
| A | Select/deselect item for offer |
| Left stick left/right | Adjust gold offer slider |
| X | Make offer |
| B | Cancel |

---

## Persuasion (all modes)

| Input | Action |
|-------|--------|
| D-pad up/down | Navigate options |
| A | Select option |
| B | Cancel |

---

## Travel / Repair / Training / Spells (all modes)

| Input | Action |
|-------|--------|
| D-pad up/down | Navigate list |
| A | Confirm / select |
| B | Cancel / Close |

---

## Rest / Wait

| Input | Action |
|-------|--------|
| D-pad left/right | Adjust hours slider |
| A | Confirm (Rest or Wait) |
| B | Cancel |

---

## Books (NOT YET IMPLEMENTED)

| Input | Action |
|-------|--------|
| L1 | Previous page |
| R1 | Next page |
| B | Close book |

---

## Options menu (pause)

| Input | Action |
|-------|--------|
| D-pad up/down | Navigate options |
| D-pad left/right | Change selected option value |
| B / Start | Close options menu |

---

## Choices popup (guard confrontation etc.)

| Input | Action |
|-------|--------|
| D-pad up/down | Navigate choices |
| A | Select choice |

---

## Notes / TBD

- Bottom barter overlay needs slider replacing step buttons
  for consistency with split mode controller scheme
- Controller navigation needs a visual focus indicator
  (highlighted border on selected item/row)
- All DS menus use same controls regardless of Bottom/Split/Top
- Vanilla menus retain existing native OpenMW controller support
- Books page turning (L1/R1) pending implementation
