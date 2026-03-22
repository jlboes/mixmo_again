# UI Requirements — Mixo-like Word Game

## Purpose

This document defines the **product and UX requirements for the frontend UI only** of a Mixo-like word game.

It is intended to guide the creation of a **Vue.js frontend** while staying strictly at the **UI/UX and interaction design level**.

This document does **not** describe:

- technical architecture
- component implementation details
- framework-specific code structure
- API contracts
- state management implementation
- backend behavior beyond what is necessary to understand the UI

---

# 1. Product Goal

The main UX goal is to make the game **pleasant, readable, fast, and low-friction**, especially on **smartphones**.

The major challenge is that the game requires all of the following at once:

- a board where words are placed
- a rack or visible area for the current letters
- a clear action to call **Mixmo**
- a way to define a **theme** when a **Joker** is used
- a comfortable way to place words on touch devices where **drag and drop is not appropriate**

The UI must solve these constraints with a design that is:

- mobile-first in interaction logic
- visually clean
- readable at a glance
- fast to use during play
- difficult to misuse by accident

---

# 2. Core UX Principle

## Main interaction model

The most important requirement is:

**Players must compose a word first, then place the whole word on the board as one object.**

This must be the primary gameplay interaction, especially on mobile.

The UI must **not rely on drag and drop** as a core action.

The UI must also **not require tile-by-tile manual placement** on mobile.

### Why this is required

The previous UX problems came from trying to reproduce a desktop-like board manipulation experience on small touch screens. That creates several issues:

- touch precision is poor on small cells
- drag and drop is unreliable and tiring on phones
- the player has to constantly switch visual attention between board and letters
- the board becomes too small to interact with comfortably
- accidental actions become too frequent

The new UI must avoid those problems by simplifying the interaction flow.

---

# 3. Main Interaction Flow

## Standard turn flow

The default player flow must be:

1. View available letters
2. Build a candidate word from those letters
3. Select a board anchor position
4. Choose direction if needed
5. Preview the full word placement
6. Confirm placement
7. If a Bandit is used, define the Bandit theme before final validation

This flow must feel quick and obvious.

## Mandatory design choice

The board interaction should be based on:

- selecting a **start cell** for the word
- previewing the full word instantly
- optionally switching orientation
- confirming the placement

The player should not need to place each individual letter separately.

---

# 4. Global UI Rules

These rules apply on all platforms unless a section explicitly says otherwise.

## 4.1 Readability

- Letters must always be clearly readable.
- Board cells must remain visually distinct.
- Current word composition must be more visually prominent than secondary actions.
- Important game actions must be instantly discoverable.

## 4.2 Interaction clarity

- The user must always understand which mode they are in.
- The UI must clearly distinguish between:
  - building a word
  - placing a word
  - confirming a special action such as Joker theme selection
- The current possible action must always be obvious.

## 4.3 Error prevention

- Critical actions must not be easy to trigger by accident.
- Invalid placements must be shown before confirmation.
- The reason for invalidity must be understandable.
- Undo or cancel must always be easy to access.

## 4.4 Smoothness

- The UI should feel continuous and stable.
- Avoid disruptive context switches.
- Avoid sudden layout jumps.
- Avoid interactions that open the system keyboard during core gameplay on mobile.

## 4.5 Mode visibility

The UI must clearly communicate when the player is in:

- **Compose mode**
- **Place mode**
- **Bandit theme selection mode**

This can be done through layout emphasis, panel states, headers, or contextual labels, but the distinction must remain obvious.

---

# 5. Required Functional UI Areas

The interface must provide the following visible zones.

## 5.1 Board area

A dedicated area where placed words are displayed and where the player chooses the word placement position.

Requirements:

- the board must always be visible during gameplay
- placed letters must be readable
- the board must support a live preview of the word being placed
- the board must visually distinguish:
  - existing letters
  - preview letters
  - conflicting cells
  - valid intersections

## 5.2 Current letters area

A dedicated area showing the letters currently available to the player.

Requirements:

- letters must be large enough to tap or click comfortably
- selected letters must visibly move into the current word composition
- letters still available vs already used in the current candidate word must be easy to understand

## 5.3 Word composition area

A visible construction area where the candidate word is assembled before placement.

Requirements:

- the word must be readable as a continuous string of tiles
- players must be able to remove letters from it easily
- players must be able to clear the whole candidate word easily
- the composition area must feel central to the experience, not secondary

## 5.4 Primary game actions area

A visible set of action controls for the main game flow.

Must include at minimum:

- confirm / validate placement
- cancel / clear current composition or current placement
- Mixmo action
- orientation switch during placement

## 5.5 Bandit theme definition UI

A dedicated interface for defining the theme associated with a Bandit.

Requirements:

- it must feel like part of the game flow, not a disconnected admin form
- it must appear only when relevant
- it must not make the player lose the context of the current word and placement

---

# 6. Mobile Design Requirements

This section defines the **mobile-first UX model** and has priority over desktop conventions.

## 6.1 Mobile design philosophy

On mobile, the gameplay must be based on:

- tap
- confirm
- preview
- simple mode transitions

It must **not** depend on:

- drag and drop
- precise tile movement
- tiny targets
- permanent zooming for normal usage
- system keyboard input for building words

### Important mobile decision

There should be **no keyboard fallback** for word entry on mobile.

The mobile experience must remain fully inside the game UI using the visible letter tiles only.

Reason:

- opening the device keyboard would break the flow
- it would visually disrupt the layout
- it would reduce the feeling of playing with letters
- it would create inconsistency between compose mode and place mode

---

## 6.2 Mobile gameplay model

Mobile must use a **two-step interaction**:

### Step 1 — Compose mode

The player builds the word from visible tiles.

### Step 2 — Place mode

The player places the already built word on the board as a whole.

This separation must be explicit in the UI.

---

## 6.3 Mobile layout structure

In portrait mode, the screen should be organized into four main zones:

### A. Header zone

Contains compact game status information.

Typical contents:

- current game status
- timer or round information if applicable
- small access to settings/help if needed

This area should stay compact and should not compete visually with gameplay.

### B. Board zone

The board occupies the upper part of the screen.

Requirements:

- large enough to understand the current state of the board
- readable without requiring constant zooming
- able to display placement previews clearly
- able to center attention on relevant placement area

### C. Word composition zone

A central strip or panel showing the candidate word.

Requirements:

- visually prominent
- easy to scan from left to right
- clearly separate from the letter rack
- easy to clear or modify

### D. Letter rack + action zone

The lower part of the screen contains:

- available letters
- main actions

This lower zone is critical because it sits in the most reachable part of the screen.

The most frequently used actions must be reachable with the thumb.

---

## 6.4 Mobile compose mode requirements

### Letter rack interaction

The mobile player must build the word by tapping letters from the rack.

Requirements:

- each tile must be comfortably tappable
- tapping a tile adds it to the current word composition
- tapping a tile already in the composition removes it or returns it to the rack through a simple interaction
- the selection state must be visually clear

### Word strip interaction

The composition strip must allow the user to review and edit the candidate word.

Required actions:

- remove the last letter
- clear the entire word
- optionally reorder in simple ways such as reverse or shuffle if desired by product direction

The strip should not attempt to reproduce freeform tile movement.

### Visual emphasis

In compose mode:

- the rack and word strip must be the primary focus
- the board remains visible but secondary
- the player should feel they are building before placing

---

## 6.5 Mobile place mode requirements

Once a word has been composed, the player enters placement mode.

### Placement interaction

The player must be able to place the word using this simple flow:

- tap a board cell to define a starting point
- see the full word preview immediately
- switch orientation if needed
- confirm placement

### Live preview

The preview must clearly show:

- all letters of the candidate word
- where they will appear
- intersections with existing letters
- conflicts or invalid areas

### Invalid placement feedback

If placement is invalid, the UI must explain why in a lightweight way.

Examples of useful feedback:

- out of bounds
- collision with a different letter
- disconnected placement
- rule violation specific to the game

The feedback must help correction, not just show failure.

### No tile-by-tile placement

The player must not be required to tap each letter onto each cell.

That interaction is explicitly out of scope for the mobile primary UX.

---

## 6.6 Mobile board requirements

The board must remain usable on a small screen.

### Visibility requirements

- the board must stay readable during normal gameplay
- the active area must be emphasized over distant irrelevant areas
- the board should visually support the current action state

### Focus management

When entering place mode, the board should visually help the player focus on the relevant region.

The UI should support ideas such as:

- centering on the area of interest
- emphasizing the candidate placement area
- reducing visual noise from unrelated board sections

### Board scale

The default view should prioritize readability over showing the entire board at once in tiny form.

It is better to show a useful readable area than an unreadable complete board.

### Touch targets

Board cell touch areas must be generous enough for reliable tap selection.

Even if the visible board grid is compact, actual touch hit zones should remain forgiving.

---

## 6.7 Mobile action hierarchy

The lower action area must clearly separate frequent actions from disruptive actions.

### Primary action

- Validate / Confirm placement

### Secondary actions

- Clear / Cancel
- Orientation switch

### Special action

- Mixmo

### Mixmo requirement

Mixmo must be highly visible but protected against accidental activation.

It must not be confused with Validate.

This may be achieved by:

- distinct visual style
- separated placement
- slightly stronger confirmation behavior than ordinary actions

The exact behavior can be refined later, but the UI must already reserve a clear and safe place for Mixmo.

---

## 6.8 Mobile Bandit theme flow

When a word includes a Joker, the player must define a theme.

### UX requirement

This must happen in a way that does not break the board placement flow.

### Recommended behavior

After the player has composed the word and chosen a valid placement, a contextual theme selection panel should appear.

A bottom sheet is the preferred mobile pattern.

### Theme panel requirements

The panel must:

- clearly explain why theme input is required
- keep the current word visible or understandable
- allow the player to enter or select a theme without losing placement context
- support confirmation and cancellation

### Important note

The theme step must feel like the final part of validating a Joker word, not like leaving the game screen.

---

## 6.9 Mobile transitions and animations

Mobile motion should support understanding, not decoration.

Recommended motion principles:

- tiles moving from rack to word strip should feel direct and lightweight
- entering place mode should visually shift emphasis toward the board
- opening the Bandit theme panel should feel like a continuation of the action
- invalid placement feedback should be immediate and unobtrusive

Avoid:

- heavy animation
- long transitions
- visual effects that slow play

---

## 6.10 Mobile orientation support

The UI should work in portrait as the primary mode.

Landscape can exist later, but the main UX must be designed and validated in portrait first.

The mobile requirement is not just responsiveness; it is a dedicated touch-first design.

---

# 7. Desktop Design Requirements

Desktop should support the same core game logic while taking advantage of a larger screen and pointer precision.

However, desktop must still remain consistent with the product principle:

- compose first
- place second

Desktop may be more flexible, but should not become a completely different product.

---

## 7.1 Desktop design philosophy

Desktop UI should provide:

- more simultaneous visibility
- more spatial comfort
- clearer separation between board and control areas
- faster access to secondary actions

Desktop can display more information at once without compromising readability.

---

## 7.2 Desktop layout structure

A desktop layout should use the available width to keep all major areas visible simultaneously.

Recommended high-level structure:

- top header for game status
- large central board area
- dedicated side or bottom panel for current letters and word composition
- clearly separated action area

A common pattern could be:

- board as the dominant central area
- right-side panel for rack, candidate word, actions, and Joker context

The exact arrangement can evolve, but the UI should feel spacious and legible.

---

## 7.3 Desktop compose mode requirements

Compose mode remains necessary on desktop.

Requirements:

- visible letter rack
- visible candidate word strip
- easy editing of the candidate word
- clear action buttons for clear, cancel, confirm path, and Mixmo

Desktop may support more advanced conveniences than mobile, but these are optional. The core UX should remain understandable even without them.

---

## 7.4 Desktop place mode requirements

Desktop placement should still support the same simple model:

- choose start cell
- preview full word
- choose orientation
- confirm

Because desktop has more screen real estate, the board can remain larger and easier to inspect during placement.

The preview should still clearly distinguish:

- preview letters
- intersections
- conflicts
- invalid cells

---

## 7.5 Desktop board requirements

On desktop, the board may occupy a much larger and more detailed area.

Requirements:

- excellent readability of letters and grid
- clear hover and selection states if pointer behavior is used
- stable visual focus during placement
- strong preview clarity

Desktop can allow richer visual assistance than mobile, but should not become overloaded.

---

## 7.6 Desktop action hierarchy

Desktop actions should remain clearly organized.

Requirements:

- Validate must still be the dominant action
- Mixmo must remain distinct and protected from accidental clicks
- Joker-related actions must remain contextual
- destructive or cancel actions must be easy to access but visually secondary

---

## 7.7 Desktop Bandit theme flow

Desktop should also treat Joker theme selection as contextual to the current word.

Recommended patterns:

- side panel section
- modal only if it remains lightweight and clearly connected to the current placement

The chosen pattern must preserve visibility of:

- the candidate word
- the placement state
- why the theme is needed

---

# 8. Shared Interaction States

The UI must represent the following states clearly.

## 8.1 Idle state

No current word is being built.

UI expectations:

- available letters visible
- board visible
- call to action to start composing

## 8.2 Composing state

The player is building a candidate word.

UI expectations:

- current word strip active
- selected letters reflected immediately
- board visible but secondary

## 8.3 Placement preview state

A candidate word exists and is being placed.

UI expectations:

- board becomes more visually important
- preview shown clearly
- orientation control visible
- validate action visible

## 8.4 Invalid placement state

The current preview violates a rule.

UI expectations:

- invalid cells or conflicts clearly marked
- concise reason available
- correction path obvious

## 8.5 Bandit theme state

The candidate word includes a Joker and requires theme definition.

UI expectations:

- theme UI appears contextually
- current word context preserved
- clear confirm/cancel path

## 8.6 Confirmed placement state

The move has been accepted.

UI expectations:

- placed word becomes part of the board
- temporary feedback confirms success
- next turn or next action becomes clear

---

# 9. Visual Design Guidance

This section is still UI-focused and not implementation-focused.

## 9.1 General tone

The interface should feel:

- clean
- modern
- playful but not childish
- focused on legibility
- responsive and lightweight

## 9.2 Letter tile design

Letter tiles must:

- feel tactile and game-like
- be readable instantly
- clearly show selected vs unselected vs placed vs preview states

## 9.3 Board cell design

Board cells must:

- remain visually ordered
- make existing words easy to parse
- make preview states obvious without confusion

## 9.4 Action button design

Buttons must communicate hierarchy clearly:

- Validate as primary
- Clear/Cancel/Orientation as supporting controls
- Mixmo as prominent but isolated

## 9.5 Information density

Do not overload the screen with too many persistent labels, indicators, or helper texts.

The interface should privilege:

- clear structure
- visible action priorities
- state-based guidance

---

# 10. Explicit Non-Goals

To keep the UX coherent, the following are not required for the initial UI requirements.

## 10.1 No mobile drag-and-drop-first design

Drag and drop is not the primary interaction model on mobile.

## 10.2 No mobile keyboard-based word entry

Mobile word construction must stay inside the game UI using tile taps only.

## 10.3 No desktop-only UX assumptions

The UI must not be designed primarily for desktop and then merely compressed for mobile.

## 10.4 No implementation detail in this document

This document must remain product/UI-oriented and avoid code or framework instructions.

---

# 11. Deliverable Expectation for UI Design Work

Any design exploration based on this document should produce interfaces that clearly separate:

- **Mobile UX** as a touch-first dedicated experience
- **Desktop UX** as a more spacious but consistent version of the same game flow

The resulting UI design should demonstrate:

- how the player composes a word
- how the player places the word on the board
- how Mixmo is accessed safely
- how Joker theme selection is handled contextually
- how the game remains comfortable on smartphone screens

---

# 12. Summary of Key Decisions

## Mandatory product decisions

- The UI focus is gameplay smoothness and clarity, especially on smartphone.
- The primary interaction is **compose first, place second**.
- Mobile must use **tap-based** interaction.
- Mobile must **not** rely on drag and drop.
- Mobile must **not** use a keyboard fallback for word entry.
- The board must support whole-word preview and confirmation.
- Joker theme selection must be contextual and integrated into the move validation flow.
- Mixmo must be visible, accessible, and protected against accidental activation.
- Desktop must remain consistent with the same gameplay logic while using extra space for comfort and clarity.

---

# 13. Product Clarifications Incorporated

This section captures the clarified product decisions that refine the UI requirements for the MVP.

## 13.1 Board size and shape

The board is effectively **unbounded**.

UI implications:

- the game board must be designed as an open play surface rather than a fixed bounded grid
- the UI must not visually imply a hard outer limit
- placement and navigation patterns must support the fact that words can continue expanding in any direction over time
- the active play area should be emphasized over theoretical infinite empty space

For UX purposes, the interface should always prioritize the **currently relevant zone of play** rather than attempting to display an entire unlimited board.

## 13.2 Mixmo behavior

Mixmo has specific availability and outcomes.

### Availability rule

- the Mixmo button must be disabled unless the player has placed all currently available letters

### When clicked

- every player receives two additional letters
- if no letters remain when Mixmo is triggered, the player who clicked Mixmo wins the game

### MVP simplification

For the MVP:

- there is no word validation requirement
- there is no theme validation requirement tied to Mixmo

### UI implications

- the disabled state of Mixmo must be very clear
- the UI should explain why Mixmo is unavailable when letters remain
- once all letters are placed, Mixmo must become obviously available
- Mixmo remains visually separated from Validate and other ordinary actions
- when Mixmo resolves, the UI must clearly communicate either:
  - that all players received two letters, or
  - that the triggering player won because no letters remained

## 13.3 Joker and Bandit clarification

There are two distinct special tile concepts.

### Joker

- Joker can be used as any letter
- for the MVP, Joker does not require theme selection

### Bandit

- Bandit behaves like a Joker, but only for the letters **K, W, X, Y, and Z**
- when a Bandit is used, the player must select a theme from a list
- the selected theme must be displayed to all players

### Pause rule during Bandit theme selection

- the game is paused while the player selects the theme
- the game resumes when the theme is selected
- during the pause, players must be unable to place letters

### UI implications

- the UI must clearly distinguish Joker and Bandit visually
- only Bandit should trigger the theme-selection flow
- the theme flow must communicate that the match is temporarily paused
- all gameplay placement interactions must be blocked during this pause state
- the selected Bandit theme must become visible in the shared game UI after confirmation

## 13.4 Placement helper suggestions in MVP

Placement helper suggestions are part of the MVP.

UI implications:

- when a candidate word is ready, the interface should help the player discover valid placements
- the helper should reduce scanning effort on both mobile and desktop
- the helper must feel assistive, not intrusive

The product direction should assume that placement assistance is a core usability feature, especially on mobile and on an unbounded board.

## 13.5 Visual direction

The visual direction should be:

- minimal
- product-like
- polished enough to feel intentionally designed and attractive

UI implications:

- avoid cluttered board-game ornamentation
- prioritize clean spacing, strong hierarchy, and refined typography
- keep the interface playful through tile treatment and interaction polish rather than decorative overload

---

# 14. Updated Requirements Driven by Clarifications

## 14.1 Unbounded board requirements

Because the board has no fixed limit, the UI must support a growing play surface.

Requirements:

- the current viewport should focus on the active cluster of placed words
- empty distant space should not dominate the screen
- entering placement mode should help the user understand where placement is likely to happen
- navigation patterns must support continued growth of the board over time
- the board should never visually depend on visible outer boundaries to make sense

### Mobile implication

On mobile, this reinforces the need to emphasize the **local relevant play area** rather than showing too much empty board.

### Desktop implication

On desktop, more of the surrounding board can be shown, but the design should still center attention around active content.

## 14.2 Mixmo UI requirements

Mixmo must behave as a special gated action.

Requirements:

- Mixmo must have a clearly visible disabled state when letters remain in the player rack
- the disabled state should be understandable without requiring trial and error
- when the player has placed all letters, Mixmo should become clearly enabled
- Mixmo should remain distinct from standard placement confirmation
- when activated, the resulting game state change must be communicated immediately

### Outcome messaging

The UI must support at least these outcomes:

- all players receive two letters
- the triggering player wins because no letters remained

## 14.3 Bandit theme selection requirements

Bandit theme selection is a mandatory contextual game flow.

Requirements:

- the theme must be selected from a list, not entered as free text
- the list must be easy to browse and select on mobile and desktop
- selecting a theme must clearly resume the game
- while theme selection is open, normal placement actions must be blocked for all players
- the selected theme must become visible to all players after confirmation

### Bandit theme list for MVP

The MVP theme list must include these options:

- Animals
- Food & Drinks
- Countries & Cities
- Nature
- Jobs / Professions
- Sports
- Technology
- Movies & Entertainment
- Transportation
- Household Objects

### Theme list UX requirements

- the list must be presented as a predefined selection UI, not as text input
- each option must be easy to tap on mobile and easy to scan on desktop
- the visual treatment should make selection fast and unambiguous
- only one theme can be selected at a time
- the currently selected theme must be clearly highlighted before confirmation

### Preferred mobile pattern

A bottom sheet remains the preferred mobile pattern, provided it clearly communicates the paused state.

### Preferred desktop pattern

A lightweight modal or strong contextual side panel is acceptable, as long as the paused state and selected word context remain obvious.

## 14.4 Theme visibility requirements

Once a Bandit theme is chosen, the theme must be visible in the shared UI.

Requirements:

- the chosen theme must be easy to find after selection
- it must be clear that the displayed theme is tied to the active or latest Bandit usage
- the display should not overpower the rest of the gameplay UI

## 14.5 Placement helper suggestion requirements

Placement helper suggestions must be present in the MVP.

Requirements:

- the helper should indicate one or more valid placement options for the current word
- suggestions must reduce board-searching effort
- the user must still feel in control of the final placement
- suggested placements must remain understandable and visually linked to the board preview

### Mobile expectation

On mobile, placement suggestions are especially important because they reduce the cognitive load of searching an unbounded board on a small screen.

### Desktop expectation

On desktop, suggestions are still useful but can be presented with more simultaneous context around the board.

---

# 15. Final Handoff Readiness Notes

This document is now substantially more precise for a Codex handoff because it includes:

- the unbounded board rule
- precise Mixmo availability and outcome behavior
- the Joker vs Bandit distinction
- the paused-game requirement during Bandit theme selection
- placement helper suggestions in the MVP
- the intended visual direction

## Additional details:

- the shared theme display persists forever
- placement suggestions should show several options at once
- board navigation freedom in the MVP should be as high as possible, and the player should be able to navigate freely across their grid
- multiplayer presence/status indicators are not needed in the first UI version

## UI implications of these additional details

### Persistent Bandit theme display

- once selected, the Bandit theme must remain visible permanently for the rest of the game
- the theme display should feel persistent and stable, not temporary or toast-like
- the UI should make it easy to understand which Bandit-related context is currently in effect without visually dominating gameplay

### Several placement suggestions at once

- the placement helper should surface multiple valid options simultaneously rather than forcing the player through a one-by-one discovery flow
- the UI should allow the player to compare options quickly
- suggested options must remain readable and clearly distinguishable from one another
- the presentation should avoid overwhelming the player, especially on mobile

### Maximum board navigation freedom in MVP

- the player should be able to navigate the board as freely as possible in the MVP
- the UI should support active exploration of the grid rather than locking the player too aggressively to the latest active cluster
- navigation should still preserve smoothness and spatial orientation
- freedom of navigation must coexist with smart focus tools that help the player return to relevant gameplay zones quickly

#### Mobile implication

- mobile should allow practical navigation across the board while keeping touch interactions simple and reliable
- the UI should help players move around the grid without making normal word placement feel cumbersome
- free navigation must not come at the cost of readability or accidental actions

#### Desktop implication

- desktop can expose even more direct spatial navigation because of the larger screen and pointer precision
- the player should be able to inspect distant areas of the board without losing access to the current action controls

### No multiplayer presence indicators in V1

- the first UI version does not need live presence indicators, activity badges, readiness states, or similar status markers for other players
- the screen should stay focused on gameplay rather than multiplayer chrome
- this absence should be treated as an intentional simplification, not as a missing visual area waiting to be filled

## MVP UI Specification Addendum

This section clarifies interaction and presentation rules for the frontend-only MVP so the interface can be implemented consistently with mock data and without backend dependencies.

### 1. Product and UX Target

Mixo is a mobile-first word placement game built around a **compose first, place second** interaction model on an effectively unbounded board.

The UX target for the MVP is:

- **Fast to understand**
- **Comfortable on smartphone**
- **Low error / low friction**
- **Readable at all times**
- **Playable without drag-and-drop**
- **Playable without keyboard input on mobile**
- **Visually lightweight, modern, and playful without feeling childish**

The board is a core part of the experience, but the UI should avoid forcing the player to constantly manipulate the viewport. Smart focus, placement assistance, and clear transitions between composition and placement are preferred over raw freedom alone.

---

### 2. Board Interaction Details

#### 2.1 General board behavior

The board must support:

- **Free pan** on touch and desktop
- **Pinch-to-zoom** on touch devices
- **Wheel / trackpad zoom** on desktop
- A visible local play area centered on the relevant cluster of already placed words

The board should open focused on the **active cluster** rather than on a meaningless global origin.

#### 2.2 Interaction modes

The board behaves differently depending on the current mode:

- **Compose mode**
  - The board remains visible
  - The board is secondary
  - The main task is building the candidate word from the rack

- **Place mode**
  - The board becomes the primary focus
  - The player selects or confirms a starting anchor
  - The player reviews a preview and chooses among valid suggestions

#### 2.3 Tap, pan, and gesture priority

To avoid accidental movement on mobile:

- A **tap** should select a cell or suggestion
- A **short touch** should never trigger a pan
- A **drag** should pan only after a small movement threshold
- Pinch gestures should always take priority for zooming
- Taps should win over pans for small gestures

#### 2.4 Zoom behavior

Zoom must support readability first.

Rules:

- The default zoom should show a readable local area, not the entire board
- The minimum zoom should preserve context around the active play cluster
- The maximum zoom should make single-cell targeting easy on touch devices
- Normal gameplay should not require repeated zooming to remain usable

#### 2.5 Viewport reset and focus tools

The MVP should provide at least these board focus utilities:

- **Recenter on current placement**
  - Centers the viewport on the selected preview

- **Recenter on active cluster**
  - Returns the view to the densest relevant zone of play

- **Fit suggestions**
  - Frames the viewport around the currently available valid placement suggestions

These actions should be exposed as lightweight utility controls, not buried in menus.

#### 2.6 Suggestion selection behavior

When a candidate word is ready for placement, the UI should compute and surface several valid placement suggestions.

Rules:

- On **mobile**, show up to **3 suggestions**
- On **desktop**, show up to **5 suggestions**
- Suggestions should appear both:
  - as **board overlays** near the suggested location
  - and as a **compact suggestion list / chip row**

When the user selects a suggestion:

- that suggestion becomes active
- the board recenters if needed
- the preview becomes visually emphasized
- Validate becomes available if the placement is valid

#### 2.7 Cycling between multiple suggestions

Multiple suggestions should be easy to review without visual overload.

Mobile behavior:

- suggestions appear in a horizontal chip row or carousel
- the player can tap a chip to activate a suggestion
- horizontal swipe on the chip row may cycle through suggestions

Desktop behavior:

- suggestions can appear in a chip row or right-side list
- each suggestion should have a short descriptive label when possible

Examples of labels:

- `Best overlap`
- `Near current cluster`
- `Extends vertically`

#### 2.8 Visual treatment of suggestions

To keep the board readable:

- **selected suggestion**
  - strong preview styling
  - highest contrast

- **non-selected suggestions**
  - lighter preview styling
  - visible but secondary

- **invalid suggestions**
  - must not be shown as selectable suggestions

If the selected suggestion is outside the current viewport, the UI should offer a one-tap way to reveal it.

#### 2.9 Orientation behavior

If both orientations are possible, the UI should expose an orientation toggle.

Rules:

- If only one orientation is valid, hide or disable the toggle
- If both are valid, the toggle should instantly update the preview
- Orientation changes should never feel like a mode switch; they are a preview refinement step

---

### 3. Responsive Rules

The interface is mobile-first, but layout behavior must be explicitly defined.

#### 3.1 Breakpoints

Use these responsive breakpoints:

- **Mobile portrait:** `< 768px`
- **Tablet / small landscape:** `768px - 1023px`
- **Desktop:** `>= 1024px`
- **Wide desktop:** `>= 1440px`

#### 3.2 Mobile portrait layout

Mobile portrait is the primary target.

Recommended vertical structure:

1. **Compact header**
2. **Board area**
3. **Candidate word strip**
4. **Rack and primary actions**

Rules:

- board occupies the upper half of the usable screen
- rack and primary actions stay reachable with the thumb
- suggestion chips sit close to the board / candidate area
- Bandit theme selection should open as a **bottom sheet**
- the main action flow should stay visible without scrolling whenever possible

#### 3.3 Tablet layout

Tablet should preserve the mobile interaction model but with more breathing room.

Rules:

- keep the board dominant
- allow a larger lower panel or a side panel in landscape
- theme selection may use a bottom sheet in portrait and a side sheet in landscape
- more suggestions can be shown simultaneously than on phone

#### 3.4 Desktop layout

Desktop should use a two-area layout:

- **Main board area**
- **Control panel** for rack, candidate word, actions, and contextual game state

Rules:

- the board remains visually dominant
- helper suggestions can appear both on the board and in the side panel
- current word, current mode, and Validate should remain visible at all times
- avoid modal overuse on desktop when a panel or side sheet is sufficient

#### 3.5 Reflow behavior

Across all breakpoints:

- buttons may wrap, but should not shrink below comfortable tap/click targets
- rack and candidate word should remain visually distinct
- the current mode, current word, and primary actions must not become hidden behind secondary panels
- tablet should remain closer to mobile than to desktop in interaction logic

---

### 4. Design System Details

The visual language should be **minimal, modern, playful, tactile, and highly legible**.

#### 4.1 Color roles

Use semantic design tokens rather than hardcoded one-off colors.

Recommended color roles:

- `bg/base`
- `bg/surface`
- `bg/elevated`
- `grid/base`
- `tile/rack`
- `tile/selected`
- `tile/placed`
- `tile/preview-valid`
- `tile/preview-conflict`
- `tile/preview-intersection`
- `accent/primary`
- `accent/special`
- `accent/theme`
- `text/primary`
- `text/secondary`
- `text/inverse`
- `state/success`
- `state/warning`
- `state/error`
- `state/disabled`

Recommended visual direction:

- neutral backgrounds
- warm, tactile tile surfaces
- a strong primary accent for Validate
- a distinct special accent for Mixmo
- clear but not aggressive error styling

#### 4.2 Spacing scale

Use an 8px-based spacing system.

Recommended scale:

- `4`
- `8`
- `12`
- `16`
- `24`
- `32`

Suggested usage:

- tight gaps inside small controls: `4-8`
- tile gaps: `8`
- panel padding on mobile: `12-16`
- panel padding on desktop: `16-24`
- major section spacing: `16-24`

#### 4.3 Typography

Use a clean sans-serif font optimized for legibility.

Recommended sizes:

- page / mode title: `20-24px`
- section headers: `16-18px`
- labels and buttons: `14-16px`
- helper text: `13-15px`
- tile letters on rack: `18-24px`
- tile letters on board: `18-28px`
- optional secondary tile metadata: `10-12px`

Typography priorities:

- letters must be highly readable
- action labels must remain concise
- avoid decorative fonts

#### 4.4 Tile sizing

Recommended sizes:

- **rack tiles mobile:** `44-52px`
- **rack tiles tablet/desktop:** `48-56px`
- **board cell visible size mobile:** `28-36px`
- **board cell visible size desktop:** `32-40px`

Touch target rule:

- touchable interactive targets should never be effectively smaller than `44px`

#### 4.5 Button variants

Define these button types:

- **Primary**
  - Validate / Confirm

- **Secondary**
  - Clear / Cancel / Switch orientation

- **Special**
  - Mixmo

- **Ghost / Utility**
  - Recenter / Fit suggestions / Reset view

- **Choice chip**
  - Theme options / Placement suggestions

#### 4.6 Interaction states

Every interactive element should support consistent states.

Required states:

- **default**
- **hover** (desktop only)
- **pressed**
- **selected**
- **disabled**
- **success**
- **error**

Behavior rules:

- **Disabled**
  - reduced contrast
  - reduced emphasis
  - optionally accompanied by helper text if the reason is important

- **Error**
  - invalid preview cells highlighted clearly
  - concise inline reason visible near the board or actions

- **Success**
  - subtle positive feedback after successful placement
  - optional toast or lightweight animation

- **Selected**
  - stronger border / elevation / contrast than default

- **Paused**
  - when a Bandit theme selection is open, background game interactions are blocked or dimmed

---

### 5. Sample UI States

The following sample states should be available in the mock frontend to make screens realistic and testable.

#### 5.1 Idle state

- rack contains: `A, R, T, S, E, N`
- candidate word is empty
- board shows an existing placed cluster
- Validate is disabled
- Mixmo is disabled
- helper text can invite the player to build a word

#### 5.2 Composing state

- rack contains: `A, R, T, S, E, N`
- candidate word: `STAR`
- used letters are visually marked in the rack
- board remains visible but secondary
- Place flow becomes available

#### 5.3 Valid placement preview

- candidate word: `STAR`
- anchor selected
- orientation: horizontal
- preview is valid
- 3 placement suggestions are available
- Validate is enabled

#### 5.4 Invalid collision preview

- candidate word: `STAR`
- anchor selected
- orientation: vertical
- one or more preview cells conflict with an existing different letter
- Validate is disabled
- inline error message visible: `Collision with different letter`

#### 5.5 Disconnected placement preview

- candidate word: `STAR`
- preview does not connect according to placement rules
- Validate is disabled
- inline error message visible: `Placement must connect to the current board`

#### 5.6 Mixmo disabled

- rack still contains remaining letters
- Mixmo is visible but disabled
- helper text visible: `Mixmo is available only when all rack letters are placed`

#### 5.7 Mixmo enabled

- rack is empty
- Mixmo becomes prominent and enabled
- state clearly indicates the player can trigger the special action

#### 5.8 Bandit placement waiting for theme

- candidate word contains a Bandit tile
- placement itself is valid
- theme selection sheet opens
- background game is paused
- normal placement actions are blocked until theme is chosen

#### 5.9 Bandit theme selected

- selected theme is stored, for example: `Animals`
- chosen theme is visible in persistent game UI
- paused state ends after confirmation

#### 5.10 Confirmed placement

- word is committed to the board
- success feedback is displayed briefly
- rack updates
- candidate word clears
- view remains near the newly placed word

---

### 6. Minimal Mock Data Shape

The frontend MVP should use a small UI-oriented mock schema.

```ts
type GameMode = "idle" | "composing" | "placing" | "banditTheme" | "confirmed";

type Orientation = "horizontal" | "vertical";

type TileKind = "normal" | "joker" | "bandit";

type CellState =
  | "empty"
  | "placed"
  | "preview-valid"
  | "preview-conflict"
  | "preview-intersection";

type ThemeOption =
  | "Animals"
  | "Food & Drinks"
  | "Countries & Cities"
  | "Nature"
  | "Jobs / Professions"
  | "Sports"
  | "Technology"
  | "Movies & Entertainment"
  | "Transportation"
  | "Household Objects";

interface RackTile {
  id: string;
  letter: string;
  kind: TileKind;
  selected: boolean;
}

interface BoardCell {
  x: number;
  y: number;
  letter?: string;
  kind?: TileKind;
  state: CellState;
  locked?: boolean;
}

interface CandidateWord {
  text: string;
  tileIds: string[];
  containsJoker: boolean;
  containsBandit: boolean;
}

interface PlacementSuggestion {
  id: string;
  x: number;
  y: number;
  orientation: Orientation;
  label: string;
  score?: number;
  visible: boolean;
  selected: boolean;
}

interface PlacementPreview {
  anchor: { x: number; y: number } | null;
  orientation: Orientation;
  cells: BoardCell[];
  isValid: boolean;
  reason?: string;
}

interface ViewportState {
  centerX: number;
  centerY: number;
  zoom: number;
}

interface ThemeState {
  isOpen: boolean;
  selectedTheme: ThemeOption | null;
  persistedTheme: ThemeOption | null;
  gamePaused: boolean;
}

interface ActionState {
  canValidate: boolean;
  canClear: boolean;
  canSwitchOrientation: boolean;
  canMixmo: boolean;
  mixmoReason?: string;
}

interface ToastState {
  type: "success" | "error" | "info";
  message: string;
}

interface GameUiState {
  mode: GameMode;
  rack: RackTile[];
  boardCells: BoardCell[];
  candidateWord: CandidateWord;
  suggestions: PlacementSuggestion[];
  preview: PlacementPreview;
  viewport: ViewportState;
  theme: ThemeState;
  actions: ActionState;
  toast?: ToastState | null;
}

6.1 Mock data usage guidance
This data shape is intentionally frontend-oriented.

It should be sufficient to render:
	•	idle screens
	•	composition screens
	•	valid and invalid placement previews
	•	Mixmo eligibility states
	•	Bandit pause and theme selection flows
	•	viewport / focus utilities
	•	suggestion selection and cycling

The mock data does not need to model backend persistence or multiplayer synchronization for this frontend validation phase.

⸻

### 7. Implementation Guidance for Frontend Validation

For the frontend-only MVP:
	•	use local mock state only
	•	simulate board and suggestion logic
	•	prioritize touch comfort and readability over completeness
	•	optimize the smartphone portrait flow first
	•	keep desktop as an adapted layout, not a separate product

If implementation tradeoffs are needed, preserve this priority order:
	1.	mobile readability
	2.	clear compose-to-place flow
	3.	low-friction placement preview
	4.	smart focus on the active board area
	5.	visual polish





---

# 16. Clarifications and Authoritative UI Decisions (2026-03-13)

This section overrides earlier ambiguous wording in the UI specification.

## 16.1 Joker vs Bandit

- **Classic Joker** is a wildcard for **all letters A-Z**.
- **Bandit** is a restricted wildcard for **K, W, X, Y, and Z only**.
- **Only Bandit triggers theme selection**.
- The UI must visually distinguish Classic Joker and Bandit.
- Any earlier mention of a generic “Joker theme” should be interpreted as **Bandit theme**.

## 16.2 Bandit theme flow

The contextual theme-selection flow must happen only when the player is **using Bandit in a placement**.

UI behavior:

- drawing Bandit into the rack does not open the theme UI by itself
- when the current candidate word includes Bandit and the placement is otherwise confirmable, the UI enters `banditTheme` mode
- background gameplay actions are blocked while the global pause is active
- after theme confirmation, the UI resumes normal gameplay and keeps the selected theme persistently visible

## 16.3 Placement rules the UI must reflect

The UI must surface these gameplay constraints clearly:

- the **first word must cross the conceptual origin**
- later placements must stay connected to the player's existing cluster
- **multiple crossings are allowed** when letters match
- disconnected islands are forbidden
- diagonal placements are forbidden

Recommended helper copy for invalid previews:

- “The first word must cross the origin.”
- “This placement must connect to your existing grid.”
- “One or more overlapping letters do not match.”
- “Bandit can only be used as K, W, X, Y, or Z.”

## 16.4 Suggestions UI

Suggestions remain mandatory in MVP.

Ranking rule:

- suggestions are ranked **only by victory count**
- the suggestion card/chip should display `victoryCount` explicitly or through a clearly understandable label
- mobile should show up to 3 ranked suggestions
- desktop should show up to 5 ranked suggestions

## 16.5 Reconnect and refresh UX

On page refresh or reconnect:

- the client must reload the authoritative room snapshot
- the rack shown to the player must be fully replaced by the server snapshot
- if MIXMO or other shared draws happened while disconnected, the player must see all letters they should now own
- any stale local compose/preview draft should be discarded unless the restored snapshot explicitly supports it

## 16.6 Visual polish scope

MVP visual polish can remain intentionally lightweight.

That means:

- prioritize correctness, readability, and fast play over animation richness
- maintain clear visual hierarchy for board, rack, candidate word, suggestions, and Mixmo button
- advanced polish, illustration, and deep theming may be postponed until after MVP
