/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.core.client.impl;

import com.google.gwt.core.client.Duration;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.GWT.UncaughtExceptionHandler;
import com.google.gwt.core.client.GwtScriptOnly;
import com.google.gwt.core.client.JavaScriptException;
import com.google.gwt.core.client.JavaScriptObject;

/**
 * Private implementation class for GWT core. This API is should not be
 * considered public or stable.
 */
public final class Impl {

  static {
    if (GWT.isScript() && StackTraceCreator.collector != null) {
      // Just enforces loading of StackTraceCreator early on, nothing else to do here...
    }
  }

  private static final int WATCHDOG_ENTRY_DEPTH_CHECK_INTERVAL_MS = 2000;

  /**
   * Used by {@link #entry0(Object, Object)} to handle reentrancy.
   */
  private static int entryDepth = 0;

  /**
   * TimeStamp indicating last scheduling of the entry depth watchdog.
   */
  private static double watchdogEntryDepthLastScheduled;

  /**
   * Timer id of the entry depth watchdog. -1 if not scheduled.
   */
  private static int watchdogEntryDepthTimerId = -1;

  /**
   * This method should be used whenever GWT code is entered from a JS context
   * and there is no GWT code in the same module on the call stack. Examples
   * include event handlers, exported methods, and module initialization.
   * <p>
   * The GWT compiler and Development Mode will provide a module-scoped
   * variable, <code>$entry</code>, which is an alias for this method.
   * <p>
   * This method can be called reentrantly, which will simply delegate to the
   * function.
   * <p>
   * The function passed to this method will be invoked via
   * <code>Function.apply()</code> with the current <code>this</code> value and
   * the invocation arguments passed to <code>$entry</code>.
   *
   * @param jsFunction a JS function to invoke, which is typically a JSNI
   *          reference to a static Java method
   * @return the value returned when <code>jsFunction</code> is invoked, or
   *         <code>undefined</code> if the UncaughtExceptionHandler catches an
   *         exception raised by <code>jsFunction</code>
   */
  public static native JavaScriptObject entry(JavaScriptObject jsFunction) /*-{
    return function() {
      if (@com.google.gwt.core.client.GWT::isScript()()) {
        return @Impl::entry0(*)(jsFunction, this, arguments);
      } else {
        var _ = @Impl::entry0(*)(jsFunction, this, arguments);
        if (_ != null) {
          // Unwraps for Development Mode (see #apply())
          _ = _.val;
        }
        return _;
      }
    };
  }-*/;

  public static native String getHostPageBaseURL() /*-{
    var s = $doc.location.href;

    // Pull off any hash.
    var i = s.indexOf('#');
    if (i != -1)
      s = s.substring(0, i);

    // Pull off any query string.
    i = s.indexOf('?');
    if (i != -1)
      s = s.substring(0, i);

    // Rip off everything after the last slash.
    i = s.lastIndexOf('/');
    if (i != -1)
      s = s.substring(0, i);

    // Ensure a final slash if non-empty.
    return s.length > 0 ? s + "/" : "";
  }-*/;

  public static native String getModuleBaseURL() /*-{
    // Check to see if DevModeRedirectHook has set an alternate value.
    // The key should match DevModeRedirectHook.js.
    var key = "__gwtDevModeHook:" + $moduleName + ":moduleBase";
    var global = $wnd || self;
    return global[key] || $moduleBase;
  }-*/;

  public static native String getModuleBaseURLForStaticFiles() /*-{
    return $moduleBase;
  }-*/;

  public static native String getModuleName() /*-{
    return $moduleName;
  }-*/;

  /**
   * Returns the obfuscated name of members in the compiled output. This is a thin wrapper around
   * JNameOf AST nodes and is therefore meaningless to implement in Development Mode.
   * If the requested member is a method, the method will not be devirtualized, inlined or prunned.
   *
   * @param jsniIdent a string literal specifying a type, field, or method. Raw
   *          type names may also be used to obtain the name of the type's seed
   *          function.
   * @return the name by which the named member can be accessed at runtime, or
   *         <code>null</code> if the requested member has been pruned from the
   *         output.
   */
  public static String getNameOf(String jsniIdent) {
    /*
     * In Production Mode, the compiler directly replaces calls to this method
     * with a string literal expression.
     */
    assert !GWT.isScript() : "ReplaceRebinds failed to replace this method";
    throw new UnsupportedOperationException(
        "Impl.getNameOf() is unimplemented in Development Mode");
  }

  public static native String getPermutationStrongName() /*-{
    return $strongName;
  }-*/;

  /**
   * UncaughtExceptionHandler that is used by unit tests to spy on uncaught
   * exceptions.
   */
  private static UncaughtExceptionHandler uncaughtExceptionHandlerForTest;

  /**
   * Set an uncaught exception handler to spy on uncaught exceptions in unit
   * tests.
   * <p>
   * Setting this method will not interfere with any exception handling logic;
   * i.e. {@link GWT#getUncaughtExceptionHandler()} will still return null if a
   * handler is not set via {@link GWT#setUncaughtExceptionHandler}.
   */
  public static void setUncaughtExceptionHandlerForTest(
      UncaughtExceptionHandler handler) {
    uncaughtExceptionHandlerForTest = handler;
  }

  private static boolean onErrorInitialized;

  public static void maybeInitializeWindowOnError() {
    if ("IGNORE".equals(System.getProperty("gwt.uncaughtexceptionhandler.windowonerror"))) {
      return;
    }
    if (onErrorInitialized) {
      return;
    }
    onErrorInitialized = true;
    boolean alwaysReport =
        "REPORT"
            .equals(System.getProperty("gwt.uncaughtexceptionhandler.windowonerror"));
    registerWindowOnError(alwaysReport);
  }

  // Make sure dev mode does not try to parse the JSNI method since it contains a reference to
  // Throwable.of which is not standard Java
  @SuppressWarnings("deprecation")
  @GwtScriptOnly
  public static native void registerWindowOnError(boolean reportAlways) /*-{
    function errorHandler(msg, url, line, column, error) {
      var throwable = @java.lang.Throwable::of(*)(error);
      @Impl::reportWindowOnError(*)(throwable);
    };

    function addOnErrorHandler(windowRef) {
      var origHandler = windowRef.onerror;
      if (origHandler && !reportAlways) {
        return;
      }

      windowRef.onerror = function() {
        errorHandler.apply(this, arguments);
        if (origHandler) {
          origHandler.apply(this, arguments);
        }
        return false;
      };
    }

    // Note we need to trap both window.onerror and $wnd.onerror
    // Chrome 58 & Safari (10.1) & HtmlUnit uses $wnd.error,
    // while FF (53) /IE (even edge) listens on window.error
    addOnErrorHandler($wnd);
    addOnErrorHandler(window);
  }-*/;

  private static void reportWindowOnError(Throwable e) {
    // If the error is coming from window.onerror that we registered on, we can not report it
    // back to the browser since we would end up being called again
    reportUncaughtException(e, false);
  }

  public static void reportUncaughtException(Throwable e) {
    reportUncaughtException(e, true);
  }

  private static void reportUncaughtException(
      Throwable e, boolean reportSwallowedExceptionToBrowser) {
    if (Impl.uncaughtExceptionHandlerForTest != null) {
      Impl.uncaughtExceptionHandlerForTest.onUncaughtException(e);
    }

    UncaughtExceptionHandler handler = GWT.getUncaughtExceptionHandler();
    if (handler != null) {
      if (handler == Impl.uncaughtExceptionHandlerForTest) {
        return; // Already reported so we're done.
      }
      // TODO(goktug): Handler might throw an exception but catching and reporting it to browser
      // here breaks assumptions of some existing hybrid apps that uses UCE for exception
      // conversion. We don't have an alternative functionality (yet) and it is too risky to include
      // the change in the release at last minute.
      handler.onUncaughtException(e);
      return; // Done.
    }

    // Make sure that the exception is not swallowed
    if (GWT.isClient() && reportSwallowedExceptionToBrowser) {
      reportToBrowser(e);
    } else {
      System.err.print("Uncaught exception ");
      e.printStackTrace(System.err);
    }
  }

  private static void reportToBrowser(Throwable e) {
    reportToBrowser(e instanceof JavaScriptException ? ((JavaScriptException) e).getThrown() : e);
  }

  private static native void reportToBrowser(Object e) /*-{
    $wnd.setTimeout(function () {
      throw e;
    }, 0);
  }-*/;

  /**
   * Indicates if <code>$entry</code> has been called.
   */
  public static boolean isEntryOnStack() {
    return entryDepth > 0;
  }

  /**
   * Indicates if <code>$entry</code> is present on the stack more than once.
   */
  public static boolean isNestedEntry() {
    return entryDepth > 1;
  }

  /**
   * Implicitly called by JavaToJavaScriptCompiler.findEntryPoints().
   */
  public static native JavaScriptObject registerEntry() /*-{
    if (@com.google.gwt.core.client.GWT::isScript()()) {
      // Assignment to $entry is done by the compiler
      return @Impl::entry(*);
    } else {
      // But we have to do in in Development Mode
      return $entry = @Impl::entry(*);
    }
  }-*/;

  private static native Object apply(Object jsFunction, Object thisObj,
      Object args) /*-{
    if (@com.google.gwt.core.client.GWT::isScript()()) {
      return jsFunction.apply(thisObj, args);
    } else {
      var _ = jsFunction.apply(thisObj, args);
      if (_ != null) {
        // Wrap for Development Mode (unwrapped in #entry())
        _ = { val: _ };
      }
      return _;
    }
  }-*/;

  /**
   * Called by ModuleSpace in Development Mode when running onModuleLoads.
   */
  private static boolean enter() {
    assert entryDepth >= 0 : "Negative entryDepth value at entry " + entryDepth;

    if (GWT.isScript() && entryDepth != 0) {
      double now = Duration.currentTimeMillis();
      if (now - watchdogEntryDepthLastScheduled > WATCHDOG_ENTRY_DEPTH_CHECK_INTERVAL_MS) {
        watchdogEntryDepthLastScheduled = now;
        watchdogEntryDepthTimerId = watchdogEntryDepthSchedule();
      }
    }

    // We want to disable some actions in the reentrant case
    if (entryDepth++ == 0) {
      SchedulerImpl.INSTANCE.flushEntryCommands();
      return true;
    }
    return false;
  }

  /**
   * Implements {@link #entry(JavaScriptObject)}.
   */
  private static Object entry0(Object jsFunction, Object thisObj, Object args) throws Throwable {
    boolean initialEntry = enter();

    try {
      /*
       * Always invoke the UCE if we have one so that the exception never
       * percolates up to the browser's event loop, even in a reentrant
       * situation.
       */
      if (GWT.getUncaughtExceptionHandler() != null) {
        /*
         * This try block is guarded by the if statement so that we don't molest
         * the exception object traveling up the stack unless we're capable of
         * doing something useful with it.
         */
        try {
          return apply(jsFunction, thisObj, args);
        } catch (Throwable t) {
          reportUncaughtException(t);
          return undefined();
        }
      } else {
        // Can't handle any exceptions, let them percolate normally
        return apply(jsFunction, thisObj, args);
      }

      /*
       * DO NOT ADD catch(Throwable t) here, it would always wrap the thrown
       * value. Instead, entry() has a general catch-all block.
       */
    } finally {
      exit(initialEntry);
    }
  }

  /**
   * Called by ModuleSpace in Development Mode when running onModuleLoads.
   */
  private static void exit(boolean initialEntry) {
    if (initialEntry) {
      SchedulerImpl.INSTANCE.flushFinallyCommands();
    }

    // Decrement after we call flush
    entryDepth--;
    assert entryDepth >= 0 : "Negative entryDepth value at exit " + entryDepth;
    if (initialEntry) {
      assert entryDepth == 0 : "Depth not 0" + entryDepth;
      if (GWT.isScript() && watchdogEntryDepthTimerId != -1) {
        watchdogEntryDepthCancel(watchdogEntryDepthTimerId);
        watchdogEntryDepthTimerId = -1;
      }
    }
  }

  private static native Object undefined() /*-{
    // Intentionally not returning a value
    return;
  }-*/;

  private static native void watchdogEntryDepthCancel(int timerId) /*-{
    clearTimeout(timerId);
  }-*/;

  private static void watchdogEntryDepthRun() {
    // Note: this must NEVER be called nested in a $entry() call.
    // This method is call from a "setTimeout": entryDepth should be set to 0.
    if (GWT.isScript() && entryDepth != 0) {
      entryDepth = 0;
    }
    watchdogEntryDepthTimerId = -1;  // Timer has run.
  }

  private static native int watchdogEntryDepthSchedule() /*-{
    return setTimeout(@Impl::watchdogEntryDepthRun(), 10);
  }-*/;
}
