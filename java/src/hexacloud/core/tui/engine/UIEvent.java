package hexacloud.core.tui.engine;

/**
 * Event interface representing TUI user interface events.
 */
public interface UIEvent {

    default long timestamp() {
        return System.currentTimeMillis();
    }

    public static class KeyPressEvent implements UIEvent {
        private final int key;
        private final long timestamp;

        public KeyPressEvent(int key) {
            this.key = key;
            this.timestamp = System.currentTimeMillis();
        }

        public int key() {
            return key;
        }

        public int getKey() {
            return key;
        }

        @Override
        public long timestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "KeyPressEvent{key=" + key + '}';
        }
    }

    public static class ResizeEvent implements UIEvent {
        private final int width;
        private final int height;
        private final long timestamp;

        public ResizeEvent(int width, int height) {
            this.width = width;
            this.height = height;
            this.timestamp = System.currentTimeMillis();
        }

        public int width() {
            return width;
        }

        public int getWidth() {
            return width;
        }

        public int height() {
            return height;
        }

        public int getHeight() {
            return height;
        }

        @Override
        public long timestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "ResizeEvent{width=" + width + ", height=" + height + '}';
        }
    }

    public static class RedrawEvent implements UIEvent {
        private final boolean immediate;
        private final long timestamp;

        public RedrawEvent() {
            this(false);
        }

        public RedrawEvent(boolean immediate) {
            this.immediate = immediate;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean immediate() {
            return immediate;
        }

        public boolean isImmediate() {
            return immediate;
        }

        @Override
        public long timestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "RedrawEvent{immediate=" + immediate + '}';
        }
    }

    public static class ActionEvent implements UIEvent {
        private final String action;
        private final Object payload;
        private final long timestamp;

        public ActionEvent(String action) {
            this(action, null);
        }

        public ActionEvent(String action, Object payload) {
            this.action = action;
            this.payload = payload;
            this.timestamp = System.currentTimeMillis();
        }

        public String action() {
            return action;
        }

        public String getAction() {
            return action;
        }

        public Object payload() {
            return payload;
        }

        public Object getPayload() {
            return payload;
        }

        @Override
        public long timestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "ActionEvent{action='" + action + "', payload=" + payload + '}';
        }
    }

    public static class ShutdownEvent implements UIEvent {
        private final long timestamp = System.currentTimeMillis();

        @Override
        public long timestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "ShutdownEvent{}";
        }
    }
}
