package com.miuix.hyperschedule.ui.component

/** 注入到页面的 Promise 桥，脚本通过 shiguangBridge / shiguangBridgePromise 调用原生 */
internal val EduPromiseBridgeScript =
    """
    (function() {
        if (window._resolveAndroidPromise) return;
        window._androidPromiseResolvers = {};
        window._androidPromiseRejectors = {};

        window._resolveAndroidPromise = function(promiseId, result) {
            if (window._androidPromiseResolvers[promiseId]) {
                window._androidPromiseResolvers[promiseId](result);
                delete window._androidPromiseResolvers[promiseId];
                delete window._androidPromiseRejectors[promiseId];
            }
        };

        window._rejectAndroidPromise = function(promiseId, error) {
            if (window._androidPromiseRejectors[promiseId]) {
                window._androidPromiseRejectors[promiseId](new Error(error));
                delete window._androidPromiseResolvers[promiseId];
                delete window._androidPromiseRejectors[promiseId];
            }
        };

        window.AndroidBridgePromise = {
            showAlert: function(title, content, confirmText) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'alert_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.showAlert(title || '', content || '', confirmText || '', promiseId);
                });
            },
            showPrompt: function(title, tip, defaultText, validatorJsFunction) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'prompt_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.showPrompt(title || '', tip || '', defaultText || '', validatorJsFunction || '', promiseId);
                });
            },
            showSingleSelection: function(title, items, defaultSelectedIndex) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'singleSelect_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    const itemsJson = (typeof items === 'string') ? items : JSON.stringify(items || []);
                    const index = (defaultSelectedIndex === undefined || defaultSelectedIndex === null) ? -1 : defaultSelectedIndex;
                    AndroidBridge.showSingleSelection(title || '', itemsJson, index, promiseId);
                });
            },
            saveImportedCourses: function(coursesJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveCourses_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.saveImportedCourses(coursesJsonString, promiseId);
                });
            },
            saveCourseConfig: function(configJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveConfig_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.saveCourseConfig(configJsonString, promiseId);
                });
            },
            savePresetTimeSlots: function(timeSlotsJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveTimeSlots_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.savePresetTimeSlots(timeSlotsJsonString, promiseId);
                });
            },
            saveComboSchedule: function(comboScheduleJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveCombo_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    const comboJson = (typeof comboScheduleJsonString === 'string') ? comboScheduleJsonString : JSON.stringify(comboScheduleJsonString || {});
                    AndroidBridge.saveComboSchedule(comboJson, promiseId);
                });
            }
        };

        window.shiguangBridge = window.shiguangBridge || window.AndroidBridge;
        window.shiguangBridgePromise = window.shiguangBridgePromise || window.AndroidBridgePromise;
    })();
    """.trimIndent()
