#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <conio.h>
#include <windows.h>

static int raw_mode_active = 0;
static DWORD orig_console_mode = 0;
static HANDLE hStdin = NULL;
static HANDLE hStdout = NULL;

JNIEXPORT void JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_initTerminal0(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    if (raw_mode_active) return;
    hStdin = GetStdHandle(STD_INPUT_HANDLE);
    hStdout = GetStdHandle(STD_OUTPUT_HANDLE);
    if (hStdin == INVALID_HANDLE_VALUE || hStdout == INVALID_HANDLE_VALUE) return;

    if (!GetConsoleMode(hStdin, &orig_console_mode)) return;

    DWORD mode = orig_console_mode;
    mode &= ~(ENABLE_LINE_INPUT | ENABLE_ECHO_INPUT);
    SetConsoleMode(hStdin, mode);

    CONSOLE_CURSOR_INFO cursorInfo;
    GetConsoleCursorInfo(hStdout, &cursorInfo);
    cursorInfo.bVisible = FALSE;
    SetConsoleCursorInfo(hStdout, &cursorInfo);

    raw_mode_active = 1;
    DWORD written = 0;
    CONSOLE_SCREEN_BUFFER_INFO csbi;
    if (GetConsoleScreenBufferInfo(hStdout, &csbi)) {
        FillConsoleOutputCharacter(hStdout, ' ', csbi.dwSize.X * csbi.dwSize.Y, (COORD){0,0}, &written);
        SetConsoleCursorPosition(hStdout, (COORD){0,0});
    }
}

JNIEXPORT void JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_resetTerminal0(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    if (!raw_mode_active) return;
    if (hStdin != NULL) SetConsoleMode(hStdin, orig_console_mode);
    if (hStdout != NULL) {
        CONSOLE_CURSOR_INFO cursorInfo;
        if (GetConsoleCursorInfo(hStdout, &cursorInfo)) {
            cursorInfo.bVisible = TRUE;
            SetConsoleCursorInfo(hStdout, &cursorInfo);
        }
    }
    raw_mode_active = 0;
}

JNIEXPORT void JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_clearScreen0(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    if (hStdout == NULL) hStdout = GetStdHandle(STD_OUTPUT_HANDLE);
    DWORD written = 0;
    CONSOLE_SCREEN_BUFFER_INFO csbi;
    if (GetConsoleScreenBufferInfo(hStdout, &csbi)) {
        FillConsoleOutputCharacter(hStdout, ' ', csbi.dwSize.X * csbi.dwSize.Y, (COORD){0,0}, &written);
        SetConsoleCursorPosition(hStdout, (COORD){0,0});
    }
}

JNIEXPORT void JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_printAt0(JNIEnv *env, jclass clazz, jint x, jint y, jstring text) {
    (void)clazz;
    if (text == NULL) return;
    const char *str = (*env)->GetStringUTFChars(env, text, NULL);
    if (str == NULL) return;
    if (hStdout == NULL) hStdout = GetStdHandle(STD_OUTPUT_HANDLE);
    COORD pos; pos.X = (SHORT)(x-1); pos.Y = (SHORT)(y-1);
    DWORD written = 0;
    SetConsoleCursorPosition(hStdout, pos);
    WriteConsoleA(hStdout, str, (DWORD)strlen(str), &written, NULL);
    (*env)->ReleaseStringUTFChars(env, text, str);
}

JNIEXPORT jint JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_readKey0(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    if (_kbhit()) {
        int c = _getch();
        if (c == 0 || c == 224) {
            int code = _getch();
            switch (code) {
                case 72: return 1000; // UP Arrow
                case 80: return 1001; // DOWN Arrow
                case 77: return 1002; // RIGHT Arrow
                case 75: return 1003; // LEFT Arrow
                default: return (jint)code;
            }
        }
        return (jint)c;
    }
    return -1;
}

JNIEXPORT jboolean JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_saveConfig0(JNIEnv *env, jclass clazz, jstring filepath, jstring content) {
    (void)clazz;
    if (filepath == NULL || content == NULL) return JNI_FALSE;
    const char *path = (*env)->GetStringUTFChars(env, filepath, NULL);
    const char *body = (*env)->GetStringUTFChars(env, content, NULL);
    if (path == NULL || body == NULL) {
        if (path) (*env)->ReleaseStringUTFChars(env, filepath, path);
        if (body) (*env)->ReleaseStringUTFChars(env, content, body);
        return JNI_FALSE;
    }

    FILE *file = fopen(path, "w");
    if (file == NULL) {
        (*env)->ReleaseStringUTFChars(env, filepath, path);
        (*env)->ReleaseStringUTFChars(env, content, body);
        return JNI_FALSE;
    }

    fprintf(file, "%s", body);
    fclose(file);

    (*env)->ReleaseStringUTFChars(env, filepath, path);
    (*env)->ReleaseStringUTFChars(env, content, body);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_getTerminalWidth0(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    if (hStdout == NULL) hStdout = GetStdHandle(STD_OUTPUT_HANDLE);
    CONSOLE_SCREEN_BUFFER_INFO csbi;
    if (GetConsoleScreenBufferInfo(hStdout, &csbi)) {
        return (jint)(csbi.srWindow.Right - csbi.srWindow.Left + 1);
    }
    return 110;
}

JNIEXPORT jint JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_getTerminalHeight0(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    if (hStdout == NULL) hStdout = GetStdHandle(STD_OUTPUT_HANDLE);
    CONSOLE_SCREEN_BUFFER_INFO csbi;
    if (GetConsoleScreenBufferInfo(hStdout, &csbi)) {
        return (jint)(csbi.srWindow.Bottom - csbi.srWindow.Top + 1);
    }
    return 24;
}

#else

#include <unistd.h>
#include <termios.h>
#include <fcntl.h>
#include <signal.h>
#include <poll.h>
#include <sys/ioctl.h>

static struct termios orig_termios;
static int orig_in_flags = -1;
static int raw_mode_active = 0;
static int opened_tty_fd = -1;
static int sigwinch_pipe[2] = {-1, -1};
static int sigwinch_installed = 0;
static struct sigaction orig_sa_winch;

/*
 * Signal handler strictly for SIGWINCH.
 * MUST NOT handle SIGSEGV, SIGINT, or SIGTERM.
 */
static void sigwinch_handler(int sig) {
    (void)sig;
    if (sigwinch_pipe[1] != -1) {
        char dummy = 1;
        ssize_t ret = write(sigwinch_pipe[1], &dummy, 1);
        (void)ret;
    }
}

static int get_term_in_fd(void) {
    if (isatty(STDIN_FILENO)) {
        return STDIN_FILENO;
    }
    if (opened_tty_fd != -1) {
        return opened_tty_fd;
    }
    opened_tty_fd = open("/dev/tty", O_RDWR | O_NONBLOCK);
    if (opened_tty_fd == -1) {
        opened_tty_fd = open("/dev/tty", O_RDONLY | O_NONBLOCK);
    }
    if (opened_tty_fd != -1) {
        return opened_tty_fd;
    }
    return STDIN_FILENO;
}

static int get_term_out_fd(void) {
    return STDOUT_FILENO;
}

static void write_tty(int fd, const char *str) {
    if (str != NULL) {
        ssize_t ret = write(fd, str, strlen(str));
        (void)ret;
    }
}

JNIEXPORT void JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_initTerminal0(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    if (raw_mode_active) return;

    int in_fd = get_term_in_fd();
    if (tcgetattr(in_fd, &orig_termios) == -1) return;

    struct termios raw = orig_termios;
    raw.c_lflag &= ~(ECHO | ICANON);
    
    if (tcsetattr(in_fd, TCSAFLUSH, &raw) == -1) return;

    orig_in_flags = fcntl(in_fd, F_GETFL, 0);
    if (orig_in_flags != -1) {
        fcntl(in_fd, F_SETFL, orig_in_flags | O_NONBLOCK);
    }

    // Set up self-pipe for SIGWINCH notification ONLY
    if (pipe(sigwinch_pipe) == 0) {
        fcntl(sigwinch_pipe[0], F_SETFL, O_NONBLOCK);
        fcntl(sigwinch_pipe[1], F_SETFL, O_NONBLOCK);

        struct sigaction sa;
        memset(&sa, 0, sizeof(sa));
        sa.sa_handler = sigwinch_handler;
        sigemptyset(&sa.sa_mask);
        sa.sa_flags = SA_RESTART;
        if (sigaction(SIGWINCH, &sa, &orig_sa_winch) == 0) {
            sigwinch_installed = 1;
        }
    }

    raw_mode_active = 1;

    int out_fd = get_term_out_fd();
    write_tty(out_fd, "\033[?1049h\033[2J\033[H\033[?25l");
}

JNIEXPORT void JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_resetTerminal0(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    if (!raw_mode_active) return;

    int in_fd = get_term_in_fd();
    tcsetattr(in_fd, TCSAFLUSH, &orig_termios);
    if (orig_in_flags != -1) {
        fcntl(in_fd, F_SETFL, orig_in_flags);
        orig_in_flags = -1;
    }

    if (sigwinch_installed) {
        sigaction(SIGWINCH, &orig_sa_winch, NULL);
        sigwinch_installed = 0;
    }

    if (sigwinch_pipe[0] != -1) {
        close(sigwinch_pipe[0]);
        sigwinch_pipe[0] = -1;
    }
    if (sigwinch_pipe[1] != -1) {
        close(sigwinch_pipe[1]);
        sigwinch_pipe[1] = -1;
    }

    raw_mode_active = 0;

    int out_fd = get_term_out_fd();
    write_tty(out_fd, "\033[?1049l\033[?25h\033[0m\n");

    if (opened_tty_fd != -1) {
        close(opened_tty_fd);
        opened_tty_fd = -1;
    }
}

JNIEXPORT void JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_clearScreen0(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    int out_fd = get_term_out_fd();
    write_tty(out_fd, "\033[2J\033[H\033[3J");
}

JNIEXPORT void JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_printAt0(JNIEnv *env, jclass clazz, jint x, jint y, jstring text) {
    (void)clazz;
    if (text == NULL) return;
    const char *str = (*env)->GetStringUTFChars(env, text, NULL);
    if (str == NULL) return;

    int out_fd = get_term_out_fd();
    char buf[512];
    int len = snprintf(buf, sizeof(buf), "\033[%d;%dH%s", y, x, str);
    if (len > 0) {
        if (len < (int)sizeof(buf)) {
            write_tty(out_fd, buf);
        } else {
            char *dynamic_buf = (char *)malloc(len + 1);
            if (dynamic_buf != NULL) {
                snprintf(dynamic_buf, len + 1, "\033[%d;%dH%s", y, x, str);
                write_tty(out_fd, dynamic_buf);
                free(dynamic_buf);
            }
        }
    }

    (*env)->ReleaseStringUTFChars(env, text, str);
}

JNIEXPORT jint JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_readKey0(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    int in_fd = get_term_in_fd();

    struct pollfd fds[2];
    int nfds = 1;
    fds[0].fd = in_fd;
    fds[0].events = POLLIN;
    fds[0].revents = 0;

    if (sigwinch_pipe[0] != -1) {
        fds[1].fd = sigwinch_pipe[0];
        fds[1].events = POLLIN;
        fds[1].revents = 0;
        nfds = 2;
    }

    int ret = poll(fds, nfds, 0); // 0ms non-blocking check
    if (ret > 0) {
        if (nfds > 1 && (fds[1].revents & POLLIN)) {
            char buf[16];
            while (read(sigwinch_pipe[0], buf, sizeof(buf)) > 0) {}
            return 2000; // Window resize signal (KEY_RESIZE)
        }
        if (fds[0].revents & POLLIN) {
            unsigned char c;
            int n = read(in_fd, &c, 1);
            if (n > 0) {
                return (jint)c; // Return raw byte
            }
        }
    }

    return -1;
}

JNIEXPORT jboolean JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_saveConfig0(JNIEnv *env, jclass clazz, jstring filepath, jstring content) {
    (void)clazz;
    if (filepath == NULL || content == NULL) return JNI_FALSE;
    const char *path = (*env)->GetStringUTFChars(env, filepath, NULL);
    const char *body = (*env)->GetStringUTFChars(env, content, NULL);
    if (path == NULL || body == NULL) {
        if (path) (*env)->ReleaseStringUTFChars(env, filepath, path);
        if (body) (*env)->ReleaseStringUTFChars(env, content, body);
        return JNI_FALSE;
    }

    FILE *file = fopen(path, "w");
    if (file == NULL) {
        (*env)->ReleaseStringUTFChars(env, filepath, path);
        (*env)->ReleaseStringUTFChars(env, content, body);
        return JNI_FALSE;
    }

    fprintf(file, "%s", body);
    fclose(file);

    (*env)->ReleaseStringUTFChars(env, filepath, path);
    (*env)->ReleaseStringUTFChars(env, content, body);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_getTerminalWidth0(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    struct winsize w;
    int out_fd = get_term_out_fd();
    if (ioctl(out_fd, TIOCGWINSZ, &w) == 0 && w.ws_col > 0) {
        return (jint)w.ws_col;
    }
    int in_fd = get_term_in_fd();
    if (ioctl(in_fd, TIOCGWINSZ, &w) == 0 && w.ws_col > 0) {
        return (jint)w.ws_col;
    }
    return 80;
}

JNIEXPORT jint JNICALL Java_hexacloud_core_utils_terminal_NativeTerminal_getTerminalHeight0(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    struct winsize w;
    int out_fd = get_term_out_fd();
    if (ioctl(out_fd, TIOCGWINSZ, &w) == 0 && w.ws_row > 0) {
        return (jint)w.ws_row;
    }
    int in_fd = get_term_in_fd();
    if (ioctl(in_fd, TIOCGWINSZ, &w) == 0 && w.ws_row > 0) {
        return (jint)w.ws_row;
    }
    return 24;
}

#endif
