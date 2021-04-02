/*
 * Window.java
 * Copyright 2021 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.os.windows.window;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.WNDENUMPROC;
import com.sun.jna.ptr.IntByReference;

/**
 * A representation of a native window.
 *
 * @author Rob Spoor
 */
public final class Window {

    private static final int WS_EX_TOPMOST = 0x00000008;
    private static final HWND HWND_TOPMOST = new HWND(Pointer.createConstant(-1));
    private static final HWND HWND_NOTOPMOST = new HWND(Pointer.createConstant(-2));

    private static User32Extended api = User32Extended.INSTANCE;

    private static Kernel32 kernel32 = Kernel32.INSTANCE;

    private final HWND handle;

    private Window(HWND handle) {
        this.handle = handle;
    }

    /**
     * Returns whether or not this window still exists.
     *
     * @return {@code true} if the window still exists, or {@code false} otherwise.
     */
    public boolean exists() {
        return api.GetWindowTextLength(handle) > 0 || kernel32.GetLastError() != WinError.ERROR_SUCCESS;
    }

    /**
     * Returns whether or not this window is visible.
     *
     * @return {@code true} if the window is visible, or {@code false} otherwise.
     *         {@code false} is also returned if the window no longer {@link #exists()}.
     */
    public boolean visible() {
        return api.IsWindowVisible(handle);
    }

    /**
     * Returns the window title.
     *
     * @return An {@link Optional} describing the window title, or {@code null} if the window has no title.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the title could not be retrieved for another reason.
     */
    public Optional<String> title() {
        int textLength = api.GetWindowTextLength(handle);
        if (textLength == 0) {
            throwLastErrorUnless(WinError.ERROR_SUCCESS);
            return Optional.empty();
        }
        char[] lpString = new char[textLength + 1];
        if (api.GetWindowText(handle, lpString, lpString.length) == 0) {
            throwLastError();
        }
        return Optional.of(Native.toString(lpString));
    }

    /**
     * Returns the window bounds.
     *
     * @return The location and size of the window as a {@link Rectangle}.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the location and could not be retrieved for another reason.
     */
    public Rectangle bounds() {
        RECT rect = new RECT();
        if (!api.GetWindowRect(handle, rect)) {
            throwLastError();
        }
        return new Rectangle(rect.left, rect.top, width(rect), height(rect));
    }

    /**
     * Moves the window without resizing it.
     * <p>
     * Note: this method does not enforce that the new location is actually on a visible screen.
     * Calling this method may result in the window to be partially or completely located outside all screen bounds.
     *
     * @param location The new location, as a {@link Point}.
     * @throws NullPointerException If the given {@link Point} is {@code null}.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window could not be moved for another reason.
     */
    public void move(Point location) {
        move(location.x, location.y);
    }

    /**
     * Moves the window without resizing it.
     * <p>
     * Note: this method does not enforce that the new location is actually on a visible screen.
     * Calling this method may result in the window to be partially or completely located outside all screen bounds.
     *
     * @param x The X-coordinate of the new location.
     * @param y The Y-coordinate of the new location.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window could not be moved for another reason.
     */
    public void move(int x, int y) {
        RECT rect = new RECT();
        if (!api.GetWindowRect(handle, rect) && !api.MoveWindow(handle, x, y, width(rect), height(rect), true)) {
            throwLastError();
        }
    }

    /**
     * Resizes the window without moving it. The top-left corner will remain the same.
     * <p>
     * Note: this method does not enforce that the new size actually fits on all visible screens.
     * Calling this method may result in the window to be partially or completely located outside all screen bounds.
     *
     * @param size The new size, as a {@link Dimension}.
     * @throws NullPointerException If the given {@link Dimension} is {@code null}.
     * @throws IllegalArgumentException If the given {@link Dimension} has a negative width or height.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window could not be resized for another reason.
     */
    public void resize(Dimension size) {
        resize(size.width, size.height);
    }

    /**
     * Resizes the window without moving it. The top-left corner will remain the same.
     * <p>
     * Note: this method does not enforce that the new size actually fits on all visible screens.
     * Calling this method may result in the window to be partially or completely located outside all screen bounds.
     *
     * @param width The new width.
     * @param height The new height.
     * @throws IllegalArgumentException If the given width or height is negative.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window could not be resized for another reason.
     */
    public void resize(int width, int height) {
        validateSize(width, height);

        RECT rect = new RECT();
        if (!api.GetWindowRect(handle, rect) && !api.MoveWindow(handle, rect.left, rect.top, width, height, true)) {
            throwLastError();
        }
    }

    /**
     * Sets the window bounds. This is a combination of moving and resizing the window.
     * <p>
     * Note: this method does not enforce that the new location is actually on a visible screen, or that the new size actually fits on all visible
     * screens. Calling this method may result in the window to be partially or completely located outside all screen bounds.
     *
     * @param bounds The new location and size, as a {@link Rectangle}.
     * @throws NullPointerException If the given {@link Rectangle} is {@code null}.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window bounds could not be set for another reason.
     */
    public void bounds(Rectangle bounds) {
        bounds(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /**
     * Sets the window bounds. This is a combination of moving and resizing the window.
     * <p>
     * Note: this method does not enforce that the new location is actually on a visible screen, or that the new size actually fits on all visible
     * screens. Calling this method may result in the window to be partially or completely located outside all screen bounds.
     *
     * @param x The X-coordinate of the new location.
     * @param y The Y-coordinate of the new location.
     * @param width The new width.
     * @param height The new height.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window bounds could not be set for another reason.
     */
    public void bounds(int x, int y, int width, int height) {
        validateSize(width, height);

        if (!api.MoveWindow(handle, x, y, width, height, true)) {
            throwLastError();
        }
    }

    private void validateSize(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException(Messages.Window.invalidSize.get(width, height));
        }
    }

    private int width(RECT rect) {
        return Math.max(0, rect.right - rect.left);
    }

    private int height(RECT rect) {
        return Math.max(0, rect.bottom - rect.top);
    }

    /**
     * Activates the window.
     *
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window could not be activated for another reason.
     */
    public void activate() {
        if (!api.SetForegroundWindow(handle)) {
            throwLastError();
        }
    }

    /**
     * Maximizes the window.
     *
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window could not be maximized for another reason.
     */
    public void maximize() {
        setState(WinUser.SW_MAXIMIZE);
    }

    /**
     * Minimizes the window.
     *
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window could not be minimized for another reason.
     */
    public void minimize() {
        setState(WinUser.SW_MINIMIZE);
    }

    /**
     * Restores the window.
     *
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window could not be restored for another reason.
     */
    public void restore() {
        setState(WinUser.SW_RESTORE);
    }

    private void setState(int nCmdShow) {
        if (!api.ShowWindow(handle, nCmdShow)) {
            throwLastError();
        }
    }

    /**
     * Returns whether or not the window is maximized.
     *
     * @return {@code true} if the window is maximized, or {@code false} otherwise.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window state could not be queried for another reason.
     */
    public boolean maximized() {
        return hasWindowLong(WinUser.GWL_STYLE, WinUser.WS_MAXIMIZE);
    }

    /**
     * Returns whether or not the window is minimized.
     *
     * @return {@code true} if the window is minimized, or {@code false} otherwise.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window state could not be queried for another reason.
     */
    public boolean minimized() {
        return hasWindowLong(WinUser.GWL_STYLE, WinUser.WS_MINIMIZE);
    }

    /**
     * Returns whether or not the window is always on top.
     *
     * @return {@code true} if the window is always on top, or {@code false} otherwise.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window state could not be queried for another reason.
     */
    public boolean alwaysOnTop() {
        return hasWindowLong(WinUser.GWL_EXSTYLE, WS_EX_TOPMOST);
    }

    /**
     * Sets whether or not the window should be always on top.
     *
     * @param alwaysOnTop {@code true} if the window should be always on top, or {@code false} otherwise.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window's always on top state could not be changed for another reason.
     */
    public void alwaysOnTop(boolean alwaysOnTop) {
        HWND hWndInsertAfter = alwaysOnTop ? HWND_TOPMOST : HWND_NOTOPMOST;
        if (!api.SetWindowPos(handle, hWndInsertAfter, 0, 0, 0, 0, WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE)) {
            throwLastError();
        }
    }

    private boolean hasWindowLong(int nIndex, int flag) {
        int value = api.GetWindowLong(handle, nIndex);
        if (value == 0) {
            throwLastError();
        }
        return (value & flag) == flag;
    }

    /**
     * Makes a request to close the window.
     * <p>
     * Note that this method does not guarantee that the window will actually be closed.
     *
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the request to close the window could not be made for another reason.
     */
    public void close() {
        api.PostMessage(handle, WinUser.WM_CLOSE, null, null);
        throwLastErrorUnless(WinError.ERROR_SUCCESS);
    }

    /**
     * Returns whether or not this window is a root window.
     *
     * @return {@code true} if this window is a root window, or {@code false} otherwise.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If it could not be determined whether or not the window is a root window for another reason.
     */
    public boolean root() {
        if (api.GetParent(handle) == null) {
            throwLastErrorUnless(WinError.ERROR_SUCCESS);
            return true;
        }
        return false;
    }

    /**
     * Returns the parent window.
     *
     * @return An {@link Optional} describing the parent window, or {@link Optional#empty()} if the window has no parent window.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window's parent window could not be retrieved for another reason.
     */
    public Optional<Window> parent() {
        HWND parent = api.GetParent(handle);
        if (parent == null) {
            throwLastErrorUnless(WinError.ERROR_SUCCESS);
            return Optional.empty();
        }
        return Optional.of(new Window(parent));
    }

    /**
     * Retrieves all visible child windows with a non-empty title.
     *
     * @return A stream with all visible child windows with a non-empty title.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window's child windows could not be retrieved for another reason.
     */
    public Stream<Window> children() {
        return children(Filter.DEFAULT);
    }

    /**
     * Retrieves all child windows that match a specific filter.
     *
     * @param filter The filter that determines which windows are returned.
     * @return A stream with all child windows that match the filter.
     * @throws NullPointerException If the filter is {@code null}.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window's child windows could not be retrieved for another reason.
     */
    public Stream<Window> children(Filter filter) {
        Objects.requireNonNull(filter);

        List<Window> windows = new ArrayList<>();
        WNDENUMPROC lpEnumFunc = wndEnumProc(windows, filter);
        if (!api.EnumChildWindows(handle, lpEnumFunc, null)) {
            throwLastError();
        }

        return windows.stream();
    }

    /**
     * Retrieves a handle to the process that created this window.
     *
     * @return An {@link Optional} describing a handle to the process that created this window,
     *         or {@link Optional#empty()} if the process is not available.
     * @throws NoSuchWindowException If the window no longer {@link #exists()}.
     * @throws WindowException If the window's process could not be retrieved for another reason.
     */
    public Optional<ProcessHandle> process() {
        IntByReference lpdwProcessId = new IntByReference();
        if (api.GetWindowThreadProcessId(handle, lpdwProcessId) == 0) {
            throwLastError();
        }
        return ProcessHandle.of(lpdwProcessId.getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        Window other = (Window) o;
        return handle.equals(other.handle);
    }

    @Override
    public int hashCode() {
        return handle.hashCode();
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        try {
            return title().orElse("<no title>");
        } catch (WindowException e) {
            return "<" + e.getMessage() + ">";
        }
    }

    /**
     * Returns all visible windows with a non-empty title.
     *
     * @return A stream with all visible windows with a non-empty title.
     */
    public static Stream<Window> allWindows() {
        return allWindows(Filter.DEFAULT);
    }

    /**
     * Returns all windows that match a specific filter.
     *
     * @param filter The filter that determines which windows are returned.
     * @return A stream with all windows that match the filter.
     * @throws NullPointerException If the filter is {@code null}.
     */
    public static Stream<Window> allWindows(Filter filter) {
        Objects.requireNonNull(filter);

        List<Window> windows = new ArrayList<>();
        WNDENUMPROC lpEnumFunc = wndEnumProc(windows, filter);
        if (!api.EnumWindows(lpEnumFunc, null)) {
            throwLastError();
        }

        return windows.stream();
    }

    private static WNDENUMPROC wndEnumProc(List<Window> windows, Filter filter) {
        return (hWnd, data) -> {
            if (filter.accept(hWnd)) {
                windows.add(new Window(hWnd));
            }
            return true;
        };
    }

    /**
     * Returns all visible windows of a specific process with a non-empty title.
     *
     * @param process A handle to the process.
     * @return A stream with all visible windows of the given process with a non-empty title.
     */
    public static Stream<Window> ofProcess(ProcessHandle process) {
        return ofProcess(process, Filter.DEFAULT);
    }

    /**
     * Returns all visible windows of a specific process that match a specific filter.
     *
     * @param process A handle to the process.
     * @param filter The filter that determines which windows are returned.
     * @return A stream with all windows of the given process that match the filter.
     * @throws NullPointerException If the process handle or filter is {@code null}.
     */
    public static Stream<Window> ofProcess(ProcessHandle process, Filter filter) {
        Objects.requireNonNull(process);
        Objects.requireNonNull(filter);

        long processId = process.pid();
        List<Window> windows = new ArrayList<>();
        WNDENUMPROC lpEnumFunc = (hWnd, data) -> {
            if (filter.accept(hWnd)) {
                IntByReference lpdwProcessId = new IntByReference();
                if (api.GetWindowThreadProcessId(hWnd, lpdwProcessId) != 0 && lpdwProcessId.getValue() == processId) {
                    windows.add(new Window(hWnd));
                }
            }
            return true;
        };
        if (!api.EnumWindows(lpEnumFunc, null)) {
            throwLastError();
        }

        return windows.stream();
    }

    /**
     * Returns a window with a specific title.
     * <p>
     * If there are multiple windows with the given title, it's unspecified which window is returned.
     *
     * @param title The title of the window to return.
     * @return An {@link Optional} describing a window with the given title, or {@link Optional#empty()} if there is no such window.
     * @throws NullPointerException If the given title is {@code null}.
     */
    public static Optional<Window> withTitle(String title) {
        Objects.requireNonNull(title);

        AtomicReference<Window> window = new AtomicReference<>();
        WNDENUMPROC lpEnumFunc = (hWnd, data) -> {
            if (api.IsWindowVisible(hWnd) && hasTitle(hWnd, title)) {
                window.set(new Window(hWnd));
                return false;
            }
            return true;
        };
        if (!api.EnumWindows(lpEnumFunc, null)) {
            throwLastErrorUnless(WinError.ERROR_SUCCESS);
        }

        return Optional.ofNullable(window.get());
    }

    private static boolean hasTitle(HWND hWnd, String title) {
        int textLength = api.GetWindowTextLength(hWnd);
        if (textLength == 0 && title.isEmpty() && kernel32.GetLastError() == WinError.ERROR_SUCCESS) {
            return true;
        }
        if (textLength > 0) {
            char[] lpString = new char[textLength + 1];
            return api.GetWindowText(hWnd, lpString, lpString.length) > 0 && Native.toString(lpString).equals(title);
        }
        return false;
    }

    private static void throwLastError() {
        int lastError = kernel32.GetLastError();
        throw error(lastError);
    }

    private static void throwLastErrorUnless(int allowed) {
        int lastError = kernel32.GetLastError();
        if (lastError != allowed) {
            throw error(lastError);
        }
    }

    private static WindowException error(int code) {
        if (code == WinError.ERROR_INVALID_WINDOW_HANDLE) {
            return new NoSuchWindowException();
        }
        throw new WindowException(code);
    }

    /**
     * A strategy for filtering windows. This can be used to determine what to return from the following methods:
     * <ul>
     * <li>{@link Window#allWindows(Filter)}</li>
     * <li>{@link Window#ofProcess(ProcessHandle, Filter)}</li>
     * <li>{@link Window#children(Filter)}</li>
     * </ul>
     *
     * @author Rob Spoor
     */
    public static final class Filter {

        private static final Filter DEFAULT = new Filter()
                .visibleOnly()
                .withTitleOnly();

        private boolean visibleOnly;
        private boolean withTitleOnly;

        /**
         * Creates a new filter. Using this filter will by default all windows.
         */
        public Filter() {
            visibleOnly = false;
            withTitleOnly = false;
        }

        /**
         * Specifies that using this filter will return only visible windows.
         *
         * @return This filter.
         */
        public Filter visibleOnly() {
            visibleOnly = true;
            return this;
        }

        /**
         * Specifies that using this filter will return only windows with a (non-empty) title.
         *
         * @return This filter.
         */
        public Filter withTitleOnly() {
            withTitleOnly = true;
            return this;
        }

        private boolean accept(HWND hWnd) {
            return (!visibleOnly || api.IsWindowVisible(hWnd))
                    && (!withTitleOnly || api.GetWindowTextLength(hWnd) > 0);
        }
    }
}
