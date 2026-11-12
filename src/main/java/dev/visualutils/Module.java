package dev.visualutils;

/** Lista modulow widocznych w menu. Kazdy mozna wlaczyc i wylaczyc (menu albo wlasny klawisz). */
public enum Module {
    TRACERS(Category.SWIAT, "Tracery", "Linie do graczy"),
    ESP(Category.SWIAT, "ESP", "Ramki wokół graczy"),
    NAMETAGS(Category.SWIAT, "Nametagi+", "Nick, HP i dystans nad celem"),
    MOBS(Category.SWIAT, "Pokazuj moby", "ESP i tracery także dla mobów"),

    FULLBRIGHT(Category.WIDOK, "Fullbright", "Pełna jasność w ciemności"),
    ZOOM(Category.WIDOK, "Zoom", "Przybliżenie (FOV 30)"),

    INFO(Category.HUD, "Info", "Koordynaty, kierunek, prędkość"),
    STATS(Category.HUD, "FPS i ping", "Licznik klatek i opóźnienie"),
    CPS(Category.HUD, "CPS", "Kliknięcia na sekundę (LPM / PPM)"),
    KEYSTROKES(Category.HUD, "Klawisze", "WASD, spacja i myszka na ekranie"),
    ARMOR(Category.HUD, "Pancerz", "Pancerz i wytrzymałość na ekranie"),
    POTIONS(Category.HUD, "Efekty", "Aktywne efekty i czas"),
    CLOCK(Category.HUD, "Zegar", "Aktualna godzina");

    /** Zakladki w menu. */
    public enum Category {
        SWIAT("Świat"),
        WIDOK("Widok"),
        HUD("HUD");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Category category;
    private final String label;
    private final String description;

    Module(Category category, String label, String description) {
        this.category = category;
        this.label = label;
        this.description = description;
    }

    public Category category() {
        return category;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
