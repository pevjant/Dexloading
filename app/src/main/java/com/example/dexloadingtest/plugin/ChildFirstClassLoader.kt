package com.example.dexloadingtest.plugin

import dalvik.system.DexClassLoader

/**
 * A custom ClassLoader that implements a "child-first" or "self-first" delegation model.
 *
 * This ClassLoader prioritizes loading classes from its own DEX files before delegating to the parent.
 * This is useful for plugin architectures where a plugin needs to use a different version of a library
 * that might also be present in the host application.
 *
 * Standard ClassLoaders use a "parent-first" model, which would prevent the plugin from loading its
 * own version of the class.
 */
class ChildFirstClassLoader(
    dexPath: String,
    optimizedDirectory: String?,
    librarySearchPath: String?,
    parent: ClassLoader
) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent) {

    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // 1. Check if the class is already loaded in this ClassLoader's cache.
        val foundClass = findLoadedClass(name)
        if (foundClass != null) {
            return foundClass
        }

        // 2. Try to find the class in this ClassLoader's DEX files.
        try {
            return findClass(name)
        } catch (e: ClassNotFoundException) {
            // Class not found in this ClassLoader.
            // Will proceed to delegate to the parent in the next step.
        }

        // 3. If not found, delegate to the parent ClassLoader.
        // This is the standard behavior.
        try {
            return super.loadClass(name, resolve)
        } catch (e: ClassNotFoundException) {
            throw ClassNotFoundException("Class '$name' not found in ChildFirstClassLoader or its parents.", e)
        }
    }
}
