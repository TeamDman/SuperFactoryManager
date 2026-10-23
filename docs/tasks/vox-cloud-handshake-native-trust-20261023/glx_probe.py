import ctypes as c
import json

x = c.CDLL('libX11.so.6')
g = c.CDLL('libGL.so.1')
x.XOpenDisplay.argtypes = [c.c_char_p]
x.XOpenDisplay.restype = c.c_void_p
x.XCloseDisplay.argtypes = [c.c_void_p]
x.XFree.argtypes = [c.c_void_p]
g.glXChooseFBConfig.argtypes = [c.c_void_p, c.c_int, c.POINTER(c.c_int), c.POINTER(c.c_int)]
g.glXChooseFBConfig.restype = c.POINTER(c.c_void_p)
g.glXCreateNewContext.argtypes = [c.c_void_p, c.c_void_p, c.c_int, c.c_void_p, c.c_int]
g.glXCreateNewContext.restype = c.c_void_p
g.glXCreatePbuffer.argtypes = [c.c_void_p, c.c_void_p, c.POINTER(c.c_int)]
g.glXCreatePbuffer.restype = c.c_ulong
g.glXMakeContextCurrent.argtypes = [c.c_void_p, c.c_ulong, c.c_ulong, c.c_void_p]
g.glXMakeContextCurrent.restype = c.c_int
g.glXDestroyPbuffer.argtypes = [c.c_void_p, c.c_ulong]
g.glXDestroyContext.argtypes = [c.c_void_p, c.c_void_p]
g.glGetString.argtypes = [c.c_uint]
g.glGetString.restype = c.c_char_p
d = x.XOpenDisplay(None)
assert d, 'XOpenDisplay failed'
attrs = (c.c_int * 11)(0x8010, 4, 0x8011, 1, 8, 8, 9, 8, 10, 8, 0)
count = c.c_int()
configs = g.glXChooseFBConfig(d, 0, attrs, c.byref(count))
assert configs and count.value > 0, 'No GLX framebuffer config'
ctx = g.glXCreateNewContext(d, configs[0], 0x8014, None, 1)
assert ctx, 'No GLX context'
pbattrs = (c.c_int * 5)(0x8041, 64, 0x8040, 64, 0)
pb = g.glXCreatePbuffer(d, configs[0], pbattrs)
assert pb and g.glXMakeContextCurrent(d, pb, pb, ctx), 'No current GLX context'
result = {name: g.glGetString(enum).decode() for name, enum in
          [('vendor', 0x1F00), ('renderer', 0x1F01), ('version', 0x1F02)]}
assert 'llvmpipe' in result['renderer']
print(json.dumps(result, indent=2))
g.glXMakeContextCurrent(d, 0, 0, None)
g.glXDestroyPbuffer(d, pb)
g.glXDestroyContext(d, ctx)
x.XFree(configs)
x.XCloseDisplay(d)
