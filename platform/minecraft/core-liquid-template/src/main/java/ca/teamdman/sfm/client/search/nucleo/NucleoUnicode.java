/* SPDX-License-Identifier: MPL-2.0
 * Generated from nucleo 8c16d47cdfa9607d3e44df5f81c635c6f43c65ee by generate_tables.py.
 * Nucleo case-fold data: Unicode 15.0.0; Rust character categories: Unicode 17.0.0.
 * Copyright Pascal Kuthe and contributors. See scripts/nucleo-parity/LICENSE-MPL-2.0.txt.
 */
package ca.teamdman.sfm.client.search.nucleo;

final class NucleoUnicode {
    private NucleoUnicode() {}
    private static int[] table(String encoded) {
        String[] values = encoded.split(" ");
        int[] result = new int[values.length];
        for (int i = 0; i < result.length; i++) result[i] = Integer.parseInt(values[i], 16);
        return result;
    }
    private static int lookup(int[] table, int point, boolean ranges) {
        int low = 0, high = table.length / 2 - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1, key = table[mid * 2];
            if (point == key) return table[mid * 2 + 1];
            if (point < key) high = mid - 1; else low = mid + 1;
        }
        return ranges ? table[high * 2 + 1] : point;
    }
    static int fold(int point) {
        if (point < 128) return point >= 'A' && point <= 'Z' ? point + 32 : point;
        return lookup(FOLD, point, false);
    }
    static int normalize(int point) {
        if (point < 0xa0 || point >= 0x20a0) return point;
        return lookup(NORMALIZE, point, false);
    }
    static int charClass(int point) { return lookup(CLASSES, point, true); }
    private static final int[] FOLD = table(
            "41 61 42 62 43 63 44 64 45 65 46 66 47 67 48 68 49 69 4a 6a 4b 6b 4c 6c 4d 6d 4e 6e 4f 6f 50 70 " +
            "51 71 52 72 53 73 54 74 55 75 56 76 57 77 58 78 59 79 5a 7a b5 3bc c0 e0 c1 e1 c2 e2 c3 e3 c4 e4 " +
            "c5 e5 c6 e6 c7 e7 c8 e8 c9 e9 ca ea cb eb cc ec cd ed ce ee cf ef d0 f0 d1 f1 d2 f2 d3 f3 d4 f4 " +
            "d5 f5 d6 f6 d8 f8 d9 f9 da fa db fb dc fc dd fd de fe 100 101 102 103 104 105 106 107 108 109 10a 10b 10c 10d " +
            "10e 10f 110 111 112 113 114 115 116 117 118 119 11a 11b 11c 11d 11e 11f 120 121 122 123 124 125 126 127 128 129 12a 12b 12c 12d " +
            "12e 12f 132 133 134 135 136 137 139 13a 13b 13c 13d 13e 13f 140 141 142 143 144 145 146 147 148 14a 14b 14c 14d 14e 14f 150 151 " +
            "152 153 154 155 156 157 158 159 15a 15b 15c 15d 15e 15f 160 161 162 163 164 165 166 167 168 169 16a 16b 16c 16d 16e 16f 170 171 " +
            "172 173 174 175 176 177 178 ff 179 17a 17b 17c 17d 17e 17f 73 181 253 182 183 184 185 186 254 187 188 189 256 18a 257 18b 18c " +
            "18e 1dd 18f 259 190 25b 191 192 193 260 194 263 196 269 197 268 198 199 19c 26f 19d 272 19f 275 1a0 1a1 1a2 1a3 1a4 1a5 1a6 280 " +
            "1a7 1a8 1a9 283 1ac 1ad 1ae 288 1af 1b0 1b1 28a 1b2 28b 1b3 1b4 1b5 1b6 1b7 292 1b8 1b9 1bc 1bd 1c4 1c6 1c5 1c6 1c7 1c9 1c8 1c9 " +
            "1ca 1cc 1cb 1cc 1cd 1ce 1cf 1d0 1d1 1d2 1d3 1d4 1d5 1d6 1d7 1d8 1d9 1da 1db 1dc 1de 1df 1e0 1e1 1e2 1e3 1e4 1e5 1e6 1e7 1e8 1e9 " +
            "1ea 1eb 1ec 1ed 1ee 1ef 1f1 1f3 1f2 1f3 1f4 1f5 1f6 195 1f7 1bf 1f8 1f9 1fa 1fb 1fc 1fd 1fe 1ff 200 201 202 203 204 205 206 207 " +
            "208 209 20a 20b 20c 20d 20e 20f 210 211 212 213 214 215 216 217 218 219 21a 21b 21c 21d 21e 21f 220 19e 222 223 224 225 226 227 " +
            "228 229 22a 22b 22c 22d 22e 22f 230 231 232 233 23a 2c65 23b 23c 23d 19a 23e 2c66 241 242 243 180 244 289 245 28c 246 247 248 249 " +
            "24a 24b 24c 24d 24e 24f 345 3b9 370 371 372 373 376 377 37f 3f3 386 3ac 388 3ad 389 3ae 38a 3af 38c 3cc 38e 3cd 38f 3ce 391 3b1 " +
            "392 3b2 393 3b3 394 3b4 395 3b5 396 3b6 397 3b7 398 3b8 399 3b9 39a 3ba 39b 3bb 39c 3bc 39d 3bd 39e 3be 39f 3bf 3a0 3c0 3a1 3c1 " +
            "3a3 3c3 3a4 3c4 3a5 3c5 3a6 3c6 3a7 3c7 3a8 3c8 3a9 3c9 3aa 3ca 3ab 3cb 3c2 3c3 3cf 3d7 3d0 3b2 3d1 3b8 3d5 3c6 3d6 3c0 3d8 3d9 " +
            "3da 3db 3dc 3dd 3de 3df 3e0 3e1 3e2 3e3 3e4 3e5 3e6 3e7 3e8 3e9 3ea 3eb 3ec 3ed 3ee 3ef 3f0 3ba 3f1 3c1 3f4 3b8 3f5 3b5 3f7 3f8 " +
            "3f9 3f2 3fa 3fb 3fd 37b 3fe 37c 3ff 37d 400 450 401 451 402 452 403 453 404 454 405 455 406 456 407 457 408 458 409 459 40a 45a " +
            "40b 45b 40c 45c 40d 45d 40e 45e 40f 45f 410 430 411 431 412 432 413 433 414 434 415 435 416 436 417 437 418 438 419 439 41a 43a " +
            "41b 43b 41c 43c 41d 43d 41e 43e 41f 43f 420 440 421 441 422 442 423 443 424 444 425 445 426 446 427 447 428 448 429 449 42a 44a " +
            "42b 44b 42c 44c 42d 44d 42e 44e 42f 44f 460 461 462 463 464 465 466 467 468 469 46a 46b 46c 46d 46e 46f 470 471 472 473 474 475 " +
            "476 477 478 479 47a 47b 47c 47d 47e 47f 480 481 48a 48b 48c 48d 48e 48f 490 491 492 493 494 495 496 497 498 499 49a 49b 49c 49d " +
            "49e 49f 4a0 4a1 4a2 4a3 4a4 4a5 4a6 4a7 4a8 4a9 4aa 4ab 4ac 4ad 4ae 4af 4b0 4b1 4b2 4b3 4b4 4b5 4b6 4b7 4b8 4b9 4ba 4bb 4bc 4bd " +
            "4be 4bf 4c0 4cf 4c1 4c2 4c3 4c4 4c5 4c6 4c7 4c8 4c9 4ca 4cb 4cc 4cd 4ce 4d0 4d1 4d2 4d3 4d4 4d5 4d6 4d7 4d8 4d9 4da 4db 4dc 4dd " +
            "4de 4df 4e0 4e1 4e2 4e3 4e4 4e5 4e6 4e7 4e8 4e9 4ea 4eb 4ec 4ed 4ee 4ef 4f0 4f1 4f2 4f3 4f4 4f5 4f6 4f7 4f8 4f9 4fa 4fb 4fc 4fd " +
            "4fe 4ff 500 501 502 503 504 505 506 507 508 509 50a 50b 50c 50d 50e 50f 510 511 512 513 514 515 516 517 518 519 51a 51b 51c 51d " +
            "51e 51f 520 521 522 523 524 525 526 527 528 529 52a 52b 52c 52d 52e 52f 531 561 532 562 533 563 534 564 535 565 536 566 537 567 " +
            "538 568 539 569 53a 56a 53b 56b 53c 56c 53d 56d 53e 56e 53f 56f 540 570 541 571 542 572 543 573 544 574 545 575 546 576 547 577 " +
            "548 578 549 579 54a 57a 54b 57b 54c 57c 54d 57d 54e 57e 54f 57f 550 580 551 581 552 582 553 583 554 584 555 585 556 586 10a0 2d00 " +
            "10a1 2d01 10a2 2d02 10a3 2d03 10a4 2d04 10a5 2d05 10a6 2d06 10a7 2d07 10a8 2d08 10a9 2d09 10aa 2d0a 10ab 2d0b 10ac 2d0c 10ad 2d0d 10ae 2d0e 10af 2d0f 10b0 2d10 " +
            "10b1 2d11 10b2 2d12 10b3 2d13 10b4 2d14 10b5 2d15 10b6 2d16 10b7 2d17 10b8 2d18 10b9 2d19 10ba 2d1a 10bb 2d1b 10bc 2d1c 10bd 2d1d 10be 2d1e 10bf 2d1f 10c0 2d20 " +
            "10c1 2d21 10c2 2d22 10c3 2d23 10c4 2d24 10c5 2d25 10c7 2d27 10cd 2d2d 13f8 13f0 13f9 13f1 13fa 13f2 13fb 13f3 13fc 13f4 13fd 13f5 1c80 432 1c81 434 1c82 43e " +
            "1c83 441 1c84 442 1c85 442 1c86 44a 1c87 463 1c88 a64b 1c90 10d0 1c91 10d1 1c92 10d2 1c93 10d3 1c94 10d4 1c95 10d5 1c96 10d6 1c97 10d7 1c98 10d8 1c99 10d9 " +
            "1c9a 10da 1c9b 10db 1c9c 10dc 1c9d 10dd 1c9e 10de 1c9f 10df 1ca0 10e0 1ca1 10e1 1ca2 10e2 1ca3 10e3 1ca4 10e4 1ca5 10e5 1ca6 10e6 1ca7 10e7 1ca8 10e8 1ca9 10e9 " +
            "1caa 10ea 1cab 10eb 1cac 10ec 1cad 10ed 1cae 10ee 1caf 10ef 1cb0 10f0 1cb1 10f1 1cb2 10f2 1cb3 10f3 1cb4 10f4 1cb5 10f5 1cb6 10f6 1cb7 10f7 1cb8 10f8 1cb9 10f9 " +
            "1cba 10fa 1cbd 10fd 1cbe 10fe 1cbf 10ff 1e00 1e01 1e02 1e03 1e04 1e05 1e06 1e07 1e08 1e09 1e0a 1e0b 1e0c 1e0d 1e0e 1e0f 1e10 1e11 1e12 1e13 1e14 1e15 1e16 1e17 " +
            "1e18 1e19 1e1a 1e1b 1e1c 1e1d 1e1e 1e1f 1e20 1e21 1e22 1e23 1e24 1e25 1e26 1e27 1e28 1e29 1e2a 1e2b 1e2c 1e2d 1e2e 1e2f 1e30 1e31 1e32 1e33 1e34 1e35 1e36 1e37 " +
            "1e38 1e39 1e3a 1e3b 1e3c 1e3d 1e3e 1e3f 1e40 1e41 1e42 1e43 1e44 1e45 1e46 1e47 1e48 1e49 1e4a 1e4b 1e4c 1e4d 1e4e 1e4f 1e50 1e51 1e52 1e53 1e54 1e55 1e56 1e57 " +
            "1e58 1e59 1e5a 1e5b 1e5c 1e5d 1e5e 1e5f 1e60 1e61 1e62 1e63 1e64 1e65 1e66 1e67 1e68 1e69 1e6a 1e6b 1e6c 1e6d 1e6e 1e6f 1e70 1e71 1e72 1e73 1e74 1e75 1e76 1e77 " +
            "1e78 1e79 1e7a 1e7b 1e7c 1e7d 1e7e 1e7f 1e80 1e81 1e82 1e83 1e84 1e85 1e86 1e87 1e88 1e89 1e8a 1e8b 1e8c 1e8d 1e8e 1e8f 1e90 1e91 1e92 1e93 1e94 1e95 1e9b 1e61 " +
            "1e9e df 1ea0 1ea1 1ea2 1ea3 1ea4 1ea5 1ea6 1ea7 1ea8 1ea9 1eaa 1eab 1eac 1ead 1eae 1eaf 1eb0 1eb1 1eb2 1eb3 1eb4 1eb5 1eb6 1eb7 1eb8 1eb9 1eba 1ebb 1ebc 1ebd " +
            "1ebe 1ebf 1ec0 1ec1 1ec2 1ec3 1ec4 1ec5 1ec6 1ec7 1ec8 1ec9 1eca 1ecb 1ecc 1ecd 1ece 1ecf 1ed0 1ed1 1ed2 1ed3 1ed4 1ed5 1ed6 1ed7 1ed8 1ed9 1eda 1edb 1edc 1edd " +
            "1ede 1edf 1ee0 1ee1 1ee2 1ee3 1ee4 1ee5 1ee6 1ee7 1ee8 1ee9 1eea 1eeb 1eec 1eed 1eee 1eef 1ef0 1ef1 1ef2 1ef3 1ef4 1ef5 1ef6 1ef7 1ef8 1ef9 1efa 1efb 1efc 1efd " +
            "1efe 1eff 1f08 1f00 1f09 1f01 1f0a 1f02 1f0b 1f03 1f0c 1f04 1f0d 1f05 1f0e 1f06 1f0f 1f07 1f18 1f10 1f19 1f11 1f1a 1f12 1f1b 1f13 1f1c 1f14 1f1d 1f15 1f28 1f20 " +
            "1f29 1f21 1f2a 1f22 1f2b 1f23 1f2c 1f24 1f2d 1f25 1f2e 1f26 1f2f 1f27 1f38 1f30 1f39 1f31 1f3a 1f32 1f3b 1f33 1f3c 1f34 1f3d 1f35 1f3e 1f36 1f3f 1f37 1f48 1f40 " +
            "1f49 1f41 1f4a 1f42 1f4b 1f43 1f4c 1f44 1f4d 1f45 1f59 1f51 1f5b 1f53 1f5d 1f55 1f5f 1f57 1f68 1f60 1f69 1f61 1f6a 1f62 1f6b 1f63 1f6c 1f64 1f6d 1f65 1f6e 1f66 " +
            "1f6f 1f67 1f88 1f80 1f89 1f81 1f8a 1f82 1f8b 1f83 1f8c 1f84 1f8d 1f85 1f8e 1f86 1f8f 1f87 1f98 1f90 1f99 1f91 1f9a 1f92 1f9b 1f93 1f9c 1f94 1f9d 1f95 1f9e 1f96 " +
            "1f9f 1f97 1fa8 1fa0 1fa9 1fa1 1faa 1fa2 1fab 1fa3 1fac 1fa4 1fad 1fa5 1fae 1fa6 1faf 1fa7 1fb8 1fb0 1fb9 1fb1 1fba 1f70 1fbb 1f71 1fbc 1fb3 1fbe 3b9 1fc8 1f72 " +
            "1fc9 1f73 1fca 1f74 1fcb 1f75 1fcc 1fc3 1fd8 1fd0 1fd9 1fd1 1fda 1f76 1fdb 1f77 1fe8 1fe0 1fe9 1fe1 1fea 1f7a 1feb 1f7b 1fec 1fe5 1ff8 1f78 1ff9 1f79 1ffa 1f7c " +
            "1ffb 1f7d 1ffc 1ff3 2126 3c9 212a 6b 212b e5 2132 214e 2160 2170 2161 2171 2162 2172 2163 2173 2164 2174 2165 2175 2166 2176 2167 2177 2168 2178 2169 2179 " +
            "216a 217a 216b 217b 216c 217c 216d 217d 216e 217e 216f 217f 2183 2184 24b6 24d0 24b7 24d1 24b8 24d2 24b9 24d3 24ba 24d4 24bb 24d5 24bc 24d6 24bd 24d7 24be 24d8 " +
            "24bf 24d9 24c0 24da 24c1 24db 24c2 24dc 24c3 24dd 24c4 24de 24c5 24df 24c6 24e0 24c7 24e1 24c8 24e2 24c9 24e3 24ca 24e4 24cb 24e5 24cc 24e6 24cd 24e7 24ce 24e8 " +
            "24cf 24e9 2c00 2c30 2c01 2c31 2c02 2c32 2c03 2c33 2c04 2c34 2c05 2c35 2c06 2c36 2c07 2c37 2c08 2c38 2c09 2c39 2c0a 2c3a 2c0b 2c3b 2c0c 2c3c 2c0d 2c3d 2c0e 2c3e " +
            "2c0f 2c3f 2c10 2c40 2c11 2c41 2c12 2c42 2c13 2c43 2c14 2c44 2c15 2c45 2c16 2c46 2c17 2c47 2c18 2c48 2c19 2c49 2c1a 2c4a 2c1b 2c4b 2c1c 2c4c 2c1d 2c4d 2c1e 2c4e " +
            "2c1f 2c4f 2c20 2c50 2c21 2c51 2c22 2c52 2c23 2c53 2c24 2c54 2c25 2c55 2c26 2c56 2c27 2c57 2c28 2c58 2c29 2c59 2c2a 2c5a 2c2b 2c5b 2c2c 2c5c 2c2d 2c5d 2c2e 2c5e " +
            "2c2f 2c5f 2c60 2c61 2c62 26b 2c63 1d7d 2c64 27d 2c67 2c68 2c69 2c6a 2c6b 2c6c 2c6d 251 2c6e 271 2c6f 250 2c70 252 2c72 2c73 2c75 2c76 2c7e 23f 2c7f 240 " +
            "2c80 2c81 2c82 2c83 2c84 2c85 2c86 2c87 2c88 2c89 2c8a 2c8b 2c8c 2c8d 2c8e 2c8f 2c90 2c91 2c92 2c93 2c94 2c95 2c96 2c97 2c98 2c99 2c9a 2c9b 2c9c 2c9d 2c9e 2c9f " +
            "2ca0 2ca1 2ca2 2ca3 2ca4 2ca5 2ca6 2ca7 2ca8 2ca9 2caa 2cab 2cac 2cad 2cae 2caf 2cb0 2cb1 2cb2 2cb3 2cb4 2cb5 2cb6 2cb7 2cb8 2cb9 2cba 2cbb 2cbc 2cbd 2cbe 2cbf " +
            "2cc0 2cc1 2cc2 2cc3 2cc4 2cc5 2cc6 2cc7 2cc8 2cc9 2cca 2ccb 2ccc 2ccd 2cce 2ccf 2cd0 2cd1 2cd2 2cd3 2cd4 2cd5 2cd6 2cd7 2cd8 2cd9 2cda 2cdb 2cdc 2cdd 2cde 2cdf " +
            "2ce0 2ce1 2ce2 2ce3 2ceb 2cec 2ced 2cee 2cf2 2cf3 a640 a641 a642 a643 a644 a645 a646 a647 a648 a649 a64a a64b a64c a64d a64e a64f a650 a651 a652 a653 a654 a655 " +
            "a656 a657 a658 a659 a65a a65b a65c a65d a65e a65f a660 a661 a662 a663 a664 a665 a666 a667 a668 a669 a66a a66b a66c a66d a680 a681 a682 a683 a684 a685 a686 a687 " +
            "a688 a689 a68a a68b a68c a68d a68e a68f a690 a691 a692 a693 a694 a695 a696 a697 a698 a699 a69a a69b a722 a723 a724 a725 a726 a727 a728 a729 a72a a72b a72c a72d " +
            "a72e a72f a732 a733 a734 a735 a736 a737 a738 a739 a73a a73b a73c a73d a73e a73f a740 a741 a742 a743 a744 a745 a746 a747 a748 a749 a74a a74b a74c a74d a74e a74f " +
            "a750 a751 a752 a753 a754 a755 a756 a757 a758 a759 a75a a75b a75c a75d a75e a75f a760 a761 a762 a763 a764 a765 a766 a767 a768 a769 a76a a76b a76c a76d a76e a76f " +
            "a779 a77a a77b a77c a77d 1d79 a77e a77f a780 a781 a782 a783 a784 a785 a786 a787 a78b a78c a78d 265 a790 a791 a792 a793 a796 a797 a798 a799 a79a a79b a79c a79d " +
            "a79e a79f a7a0 a7a1 a7a2 a7a3 a7a4 a7a5 a7a6 a7a7 a7a8 a7a9 a7aa 266 a7ab 25c a7ac 261 a7ad 26c a7ae 26a a7b0 29e a7b1 287 a7b2 29d a7b3 ab53 a7b4 a7b5 " +
            "a7b6 a7b7 a7b8 a7b9 a7ba a7bb a7bc a7bd a7be a7bf a7c0 a7c1 a7c2 a7c3 a7c4 a794 a7c5 282 a7c6 1d8e a7c7 a7c8 a7c9 a7ca a7d0 a7d1 a7d6 a7d7 a7d8 a7d9 a7f5 a7f6 " +
            "ab70 13a0 ab71 13a1 ab72 13a2 ab73 13a3 ab74 13a4 ab75 13a5 ab76 13a6 ab77 13a7 ab78 13a8 ab79 13a9 ab7a 13aa ab7b 13ab ab7c 13ac ab7d 13ad ab7e 13ae ab7f 13af " +
            "ab80 13b0 ab81 13b1 ab82 13b2 ab83 13b3 ab84 13b4 ab85 13b5 ab86 13b6 ab87 13b7 ab88 13b8 ab89 13b9 ab8a 13ba ab8b 13bb ab8c 13bc ab8d 13bd ab8e 13be ab8f 13bf " +
            "ab90 13c0 ab91 13c1 ab92 13c2 ab93 13c3 ab94 13c4 ab95 13c5 ab96 13c6 ab97 13c7 ab98 13c8 ab99 13c9 ab9a 13ca ab9b 13cb ab9c 13cc ab9d 13cd ab9e 13ce ab9f 13cf " +
            "aba0 13d0 aba1 13d1 aba2 13d2 aba3 13d3 aba4 13d4 aba5 13d5 aba6 13d6 aba7 13d7 aba8 13d8 aba9 13d9 abaa 13da abab 13db abac 13dc abad 13dd abae 13de abaf 13df " +
            "abb0 13e0 abb1 13e1 abb2 13e2 abb3 13e3 abb4 13e4 abb5 13e5 abb6 13e6 abb7 13e7 abb8 13e8 abb9 13e9 abba 13ea abbb 13eb abbc 13ec abbd 13ed abbe 13ee abbf 13ef " +
            "ff21 ff41 ff22 ff42 ff23 ff43 ff24 ff44 ff25 ff45 ff26 ff46 ff27 ff47 ff28 ff48 ff29 ff49 ff2a ff4a ff2b ff4b ff2c ff4c ff2d ff4d ff2e ff4e ff2f ff4f ff30 ff50 " +
            "ff31 ff51 ff32 ff52 ff33 ff53 ff34 ff54 ff35 ff55 ff36 ff56 ff37 ff57 ff38 ff58 ff39 ff59 ff3a ff5a 10400 10428 10401 10429 10402 1042a 10403 1042b 10404 1042c 10405 1042d " +
            "10406 1042e 10407 1042f 10408 10430 10409 10431 1040a 10432 1040b 10433 1040c 10434 1040d 10435 1040e 10436 1040f 10437 10410 10438 10411 10439 10412 1043a 10413 1043b 10414 1043c 10415 1043d " +
            "10416 1043e 10417 1043f 10418 10440 10419 10441 1041a 10442 1041b 10443 1041c 10444 1041d 10445 1041e 10446 1041f 10447 10420 10448 10421 10449 10422 1044a 10423 1044b 10424 1044c 10425 1044d " +
            "10426 1044e 10427 1044f 104b0 104d8 104b1 104d9 104b2 104da 104b3 104db 104b4 104dc 104b5 104dd 104b6 104de 104b7 104df 104b8 104e0 104b9 104e1 104ba 104e2 104bb 104e3 104bc 104e4 104bd 104e5 " +
            "104be 104e6 104bf 104e7 104c0 104e8 104c1 104e9 104c2 104ea 104c3 104eb 104c4 104ec 104c5 104ed 104c6 104ee 104c7 104ef 104c8 104f0 104c9 104f1 104ca 104f2 104cb 104f3 104cc 104f4 104cd 104f5 " +
            "104ce 104f6 104cf 104f7 104d0 104f8 104d1 104f9 104d2 104fa 104d3 104fb 10570 10597 10571 10598 10572 10599 10573 1059a 10574 1059b 10575 1059c 10576 1059d 10577 1059e 10578 1059f 10579 105a0 " +
            "1057a 105a1 1057c 105a3 1057d 105a4 1057e 105a5 1057f 105a6 10580 105a7 10581 105a8 10582 105a9 10583 105aa 10584 105ab 10585 105ac 10586 105ad 10587 105ae 10588 105af 10589 105b0 1058a 105b1 " +
            "1058c 105b3 1058d 105b4 1058e 105b5 1058f 105b6 10590 105b7 10591 105b8 10592 105b9 10594 105bb 10595 105bc 10c80 10cc0 10c81 10cc1 10c82 10cc2 10c83 10cc3 10c84 10cc4 10c85 10cc5 10c86 10cc6 " +
            "10c87 10cc7 10c88 10cc8 10c89 10cc9 10c8a 10cca 10c8b 10ccb 10c8c 10ccc 10c8d 10ccd 10c8e 10cce 10c8f 10ccf 10c90 10cd0 10c91 10cd1 10c92 10cd2 10c93 10cd3 10c94 10cd4 10c95 10cd5 10c96 10cd6 " +
            "10c97 10cd7 10c98 10cd8 10c99 10cd9 10c9a 10cda 10c9b 10cdb 10c9c 10cdc 10c9d 10cdd 10c9e 10cde 10c9f 10cdf 10ca0 10ce0 10ca1 10ce1 10ca2 10ce2 10ca3 10ce3 10ca4 10ce4 10ca5 10ce5 10ca6 10ce6 " +
            "10ca7 10ce7 10ca8 10ce8 10ca9 10ce9 10caa 10cea 10cab 10ceb 10cac 10cec 10cad 10ced 10cae 10cee 10caf 10cef 10cb0 10cf0 10cb1 10cf1 10cb2 10cf2 118a0 118c0 118a1 118c1 118a2 118c2 118a3 118c3 " +
            "118a4 118c4 118a5 118c5 118a6 118c6 118a7 118c7 118a8 118c8 118a9 118c9 118aa 118ca 118ab 118cb 118ac 118cc 118ad 118cd 118ae 118ce 118af 118cf 118b0 118d0 118b1 118d1 118b2 118d2 118b3 118d3 " +
            "118b4 118d4 118b5 118d5 118b6 118d6 118b7 118d7 118b8 118d8 118b9 118d9 118ba 118da 118bb 118db 118bc 118dc 118bd 118dd 118be 118de 118bf 118df 16e40 16e60 16e41 16e61 16e42 16e62 16e43 16e63 " +
            "16e44 16e64 16e45 16e65 16e46 16e66 16e47 16e67 16e48 16e68 16e49 16e69 16e4a 16e6a 16e4b 16e6b 16e4c 16e6c 16e4d 16e6d 16e4e 16e6e 16e4f 16e6f 16e50 16e70 16e51 16e71 16e52 16e72 16e53 16e73 " +
            "16e54 16e74 16e55 16e75 16e56 16e76 16e57 16e77 16e58 16e78 16e59 16e79 16e5a 16e7a 16e5b 16e7b 16e5c 16e7c 16e5d 16e7d 16e5e 16e7e 16e5f 16e7f 1e900 1e922 1e901 1e923 1e902 1e924 1e903 1e925 " +
            "1e904 1e926 1e905 1e927 1e906 1e928 1e907 1e929 1e908 1e92a 1e909 1e92b 1e90a 1e92c 1e90b 1e92d 1e90c 1e92e 1e90d 1e92f 1e90e 1e930 1e90f 1e931 1e910 1e932 1e911 1e933 1e912 1e934 1e913 1e935 " +
            "1e914 1e936 1e915 1e937 1e916 1e938 1e917 1e939 1e918 1e93a 1e919 1e93b 1e91a 1e93c 1e91b 1e93d 1e91c 1e93e 1e91d 1e93f 1e91e 1e940 1e91f 1e941 1e920 1e942 1e921 1e943");
    private static final int[] NORMALIZE = table(
            "a1 21 aa 61 b2 32 b3 33 b9 31 ba 30 bf 3f c0 41 c1 41 c2 41 c3 41 c4 41 c5 41 c7 43 c8 45 c9 45 " +
            "ca 45 cb 45 cc 49 cd 49 ce 49 cf 49 d0 44 d1 4e d2 4f d3 4f d4 4f d5 4f d6 4f d8 4f d9 55 da 55 " +
            "db 55 dc 55 dd 59 df 73 e0 61 e1 61 e2 61 e3 61 e4 61 e5 61 e7 63 e8 65 e9 65 ea 65 eb 65 ec 69 " +
            "ed 69 ee 69 ef 69 f0 64 f1 6e f2 6f f3 6f f4 6f f5 6f f6 6f f8 6f f9 75 fa 75 fb 75 fc 75 fd 79 " +
            "ff 79 100 41 101 61 102 41 103 61 104 41 105 61 106 43 107 63 108 43 109 63 10a 43 10b 63 10c 43 10d 63 10e 44 " +
            "10f 64 110 44 111 64 112 45 113 65 114 45 115 65 116 45 117 65 118 45 119 65 11a 45 11b 65 11c 47 11d 67 11e 47 " +
            "11f 67 120 47 121 67 122 47 123 67 124 48 125 68 126 48 127 68 128 49 129 69 12a 49 12b 69 12c 49 12d 69 12e 49 " +
            "12f 69 130 49 131 69 134 4a 135 6a 136 4b 137 6b 139 4c 13a 6c 13b 4c 13c 6c 13d 4c 13e 6c 13f 4c 140 6c 141 4c " +
            "142 6c 143 4e 144 6e 145 4e 146 6e 147 4e 148 6e 149 6e 14a 4e 14b 6e 14c 4f 14d 6f 14e 4f 14f 6f 150 4f 151 6f " +
            "154 52 155 72 156 52 157 72 158 52 159 72 15a 53 15b 73 15c 53 15d 73 15e 53 15f 73 160 53 161 73 162 54 163 74 " +
            "164 54 165 74 166 54 167 74 168 55 169 75 16a 55 16b 75 16c 55 16d 75 16e 55 16f 75 170 55 171 75 172 55 173 75 " +
            "174 57 175 77 176 59 177 79 178 59 179 5a 17a 7a 17b 5a 17c 7a 17d 5a 17e 7a 17f 73 180 62 181 42 182 62 183 62 " +
            "184 62 186 4f 187 43 188 63 189 44 18a 44 18b 64 18c 64 18e 45 18f 65 190 45 191 46 192 66 193 47 195 68 196 49 " +
            "197 49 199 6b 19a 6c 19c 4d 19d 4e 19e 6e 19f 4f 1a0 4f 1a1 6f 1a4 50 1a5 70 1a6 52 1a7 53 1a8 73 1aa 6c 1ab 74 " +
            "1ac 54 1ad 74 1ae 54 1af 55 1b0 75 1b2 56 1b3 59 1b4 79 1b5 5a 1b6 7a 1c3 21 1cd 41 1ce 61 1cf 49 1d0 69 1d1 4f " +
            "1d2 6f 1d3 55 1d4 75 1d5 55 1d6 75 1d7 55 1d8 75 1d9 55 1da 75 1db 55 1dc 75 1dd 65 1de 41 1df 61 1e0 41 1e1 61 " +
            "1e2 c6 1e3 e6 1e4 47 1e5 67 1e6 47 1e7 67 1e8 4b 1e9 6b 1ea 4f 1eb 6f 1ec 4f 1ed 6f 1f0 6a 1f4 47 1f5 67 1f8 4e " +
            "1f9 6e 1fa 41 1fb 61 1fc c6 1fd e6 1fe 4f 1ff 6f 200 41 201 61 202 41 203 61 204 45 205 65 206 45 207 65 208 49 " +
            "209 69 20a 49 20b 69 20c 4f 20d 6f 20e 4f 20f 6f 210 52 211 72 212 52 213 72 214 55 215 75 216 55 217 75 218 53 " +
            "219 73 21a 54 21b 74 21e 48 21f 68 220 4e 221 64 224 5a 225 7a 226 41 227 61 228 45 229 65 22a 4f 22b 6f 22c 4f " +
            "22d 6f 22e 4f 22f 6f 230 4f 231 6f 232 59 233 79 234 6c 235 6e 236 74 237 6a 23a 41 23b 43 23c 63 23d 4c 23e 54 " +
            "23f 73 240 7a 243 42 244 55 245 56 246 45 247 65 248 4a 249 6a 24a 51 24b 71 24c 52 24d 72 24e 59 24f 79 250 61 " +
            "251 61 252 61 253 62 254 63 255 63 256 64 257 64 258 65 259 65 25a 65 25b 65 25c 65 25d 65 25e 65 25f 6a 260 67 " +
            "261 67 262 47 263 67 264 75 265 68 266 68 267 68 268 69 269 69 26a 49 26b 6c 26c 6c 26d 6c 26f 6d 270 6d 271 6d " +
            "272 6e 273 6e 274 4e 275 6f 279 72 27a 72 27b 72 27c 72 27d 72 27e 72 27f 72 280 52 281 52 282 73 287 74 288 74 " +
            "289 75 28b 76 28c 76 28d 77 28e 79 28f 59 290 7a 291 7a 297 63 299 42 29a 65 29b 47 29c 48 29d 6a 29e 6b 29f 4c " +
            "1e00 41 1e01 61 1e02 42 1e03 62 1e04 42 1e05 62 1e06 42 1e07 62 1e08 43 1e09 63 1e0a 44 1e0b 65 1e0c 44 1e0d 64 1e0e 44 1e0f 64 " +
            "1e10 44 1e11 64 1e12 44 1e13 64 1e14 45 1e15 65 1e16 45 1e17 65 1e18 45 1e19 65 1e1a 45 1e1b 65 1e1c 45 1e1d 65 1e1e 46 1e1f 66 " +
            "1e20 47 1e21 67 1e22 48 1e23 67 1e24 48 1e25 67 1e26 48 1e27 67 1e28 48 1e29 67 1e2a 48 1e2b 68 1e2c 49 1e2d 69 1e2e 49 1e2f 69 " +
            "1e30 4b 1e31 6b 1e32 4b 1e33 6b 1e34 4b 1e35 6b 1e36 4c 1e37 6c 1e38 4c 1e39 6c 1e3a 4c 1e3b 6c 1e3c 4c 1e3d 6c 1e3e 4d 1e3f 6d " +
            "1e40 4d 1e41 6d 1e42 4d 1e43 6d 1e44 4e 1e45 6e 1e46 4e 1e47 6e 1e48 4e 1e49 6e 1e4a 4e 1e4b 6e 1e4c 4f 1e4d 6f 1e4e 4f 1e4f 6f " +
            "1e50 4f 1e51 6f 1e52 4f 1e53 6f 1e54 50 1e55 70 1e56 50 1e57 70 1e58 52 1e59 72 1e5a 52 1e5b 72 1e5c 52 1e5d 72 1e5e 52 1e5f 72 " +
            "1e60 53 1e61 73 1e62 53 1e63 73 1e64 53 1e65 73 1e66 53 1e67 73 1e68 53 1e69 73 1e6a 54 1e6b 74 1e6c 54 1e6d 74 1e6e 54 1e6f 74 " +
            "1e70 54 1e71 74 1e72 55 1e73 75 1e74 55 1e75 75 1e76 55 1e77 75 1e78 55 1e79 75 1e7a 55 1e7b 75 1e7c 56 1e7d 76 1e7e 56 1e7f 76 " +
            "1e80 57 1e81 77 1e82 57 1e83 77 1e84 57 1e85 77 1e86 57 1e87 77 1e88 57 1e89 6a 1e8a 58 1e8b 78 1e8c 58 1e8d 78 1e8e 59 1e8f 79 " +
            "1e90 5a 1e91 7a 1e92 5a 1e93 7a 1e94 5a 1e95 7a 1e96 68 1e97 74 1e98 77 1e99 79 1e9a 61 1e9b 69 1e9c 66 1e9d 66 1ea0 41 1ea1 61 " +
            "1ea2 41 1ea3 61 1ea4 41 1ea5 61 1ea6 41 1ea7 61 1ea8 41 1ea9 61 1eaa 41 1eab 61 1eac 41 1ead 61 1eae 41 1eaf 61 1eb0 41 1eb1 61 " +
            "1eb2 41 1eb3 61 1eb4 41 1eb5 61 1eb6 41 1eb7 61 1eb8 45 1eb9 65 1eba 45 1ebb 65 1ebc 45 1ebd 65 1ebe 45 1ebf 65 1ec0 45 1ec1 65 " +
            "1ec2 45 1ec3 65 1ec4 45 1ec5 65 1ec6 45 1ec7 65 1ec8 49 1ec9 69 1eca 49 1ecb 69 1ecc 4f 1ecd 6f 1ece 4f 1ecf 6f 1ed0 4f 1ed1 6f " +
            "1ed2 4f 1ed3 6f 1ed4 4f 1ed5 6f 1ed6 4f 1ed7 6f 1ed8 4f 1ed9 6f 1eda 4f 1edb 6f 1edc 4f 1edd 6f 1ede 4f 1edf 6f 1ee0 4f 1ee1 6f " +
            "1ee2 4f 1ee3 6f 1ee4 55 1ee5 75 1ee6 55 1ee7 75 1ee8 55 1ee9 75 1eea 55 1eeb 75 1eec 55 1eed 75 1eee 55 1eef 75 1ef0 55 1ef1 75 " +
            "1ef2 59 1ef3 79 1ef4 59 1ef5 79 1ef6 59 1ef7 79 1ef8 59 1ef9 79 2070 30 2071 69 2074 34 2075 35 2076 36 2077 37 2078 38 2079 30 " +
            "207a 2b 207b 2d 207c 3d 207d 28 207e 29 207f 6e 2080 30 2081 31 2082 32 2083 33 2084 34 2085 35 2086 36 2087 37 2088 38 2089 39 " +
            "208a 2b 208b 2d 208c 3d 208d 28 208e 29 2090 61 2091 65 2092 6f 2093 78 2094 65 2095 68 2096 6b 2097 6c 2098 6d 2099 6e 209a 70 " +
            "209b 73 209c 74");
    private static final int[] CLASSES = table(
            "0 1 9 0 e 1 20 0 21 1 30 6 3a 1 41 4 5b 1 61 3 7b 1 85 0 86 1 a0 0 a1 1 aa 3 " +
            "ab 1 b2 6 b4 1 b5 3 b6 1 b9 6 ba 3 bb 1 bc 6 bf 1 c0 4 d7 1 d8 4 df 3 f7 1 f8 3 " +
            "100 4 101 3 102 4 103 3 104 4 105 3 106 4 107 3 108 4 109 3 10a 4 10b 3 10c 4 10d 3 10e 4 10f 3 " +
            "110 4 111 3 112 4 113 3 114 4 115 3 116 4 117 3 118 4 119 3 11a 4 11b 3 11c 4 11d 3 11e 4 11f 3 " +
            "120 4 121 3 122 4 123 3 124 4 125 3 126 4 127 3 128 4 129 3 12a 4 12b 3 12c 4 12d 3 12e 4 12f 3 " +
            "130 5 131 3 132 4 133 3 134 4 135 3 136 4 137 3 139 4 13a 3 13b 4 13c 3 13d 4 13e 3 13f 4 140 3 " +
            "141 4 142 3 143 4 144 3 145 4 146 3 147 4 148 3 14a 4 14b 3 14c 4 14d 3 14e 4 14f 3 150 4 151 3 " +
            "152 4 153 3 154 4 155 3 156 4 157 3 158 4 159 3 15a 4 15b 3 15c 4 15d 3 15e 4 15f 3 160 4 161 3 " +
            "162 4 163 3 164 4 165 3 166 4 167 3 168 4 169 3 16a 4 16b 3 16c 4 16d 3 16e 4 16f 3 170 4 171 3 " +
            "172 4 173 3 174 4 175 3 176 4 177 3 178 4 17a 3 17b 4 17c 3 17d 4 17e 3 181 4 183 3 184 4 185 3 " +
            "186 4 188 3 189 4 18c 3 18e 4 192 3 193 4 195 3 196 4 199 3 19c 4 19e 3 19f 4 1a1 3 1a2 4 1a3 3 " +
            "1a4 4 1a5 3 1a6 4 1a8 3 1a9 4 1aa 3 1ac 4 1ad 3 1ae 4 1b0 3 1b1 4 1b4 3 1b5 4 1b6 3 1b7 4 1b9 3 " +
            "1bb 5 1bc 4 1bd 3 1c0 5 1c4 4 1c6 3 1c7 4 1c9 3 1ca 4 1cc 3 1cd 4 1ce 3 1cf 4 1d0 3 1d1 4 1d2 3 " +
            "1d3 4 1d4 3 1d5 4 1d6 3 1d7 4 1d8 3 1d9 4 1da 3 1db 4 1dc 3 1de 4 1df 3 1e0 4 1e1 3 1e2 4 1e3 3 " +
            "1e4 4 1e5 3 1e6 4 1e7 3 1e8 4 1e9 3 1ea 4 1eb 3 1ec 4 1ed 3 1ee 4 1ef 3 1f1 4 1f3 3 1f4 4 1f5 3 " +
            "1f6 4 1f9 3 1fa 4 1fb 3 1fc 4 1fd 3 1fe 4 1ff 3 200 4 201 3 202 4 203 3 204 4 205 3 206 4 207 3 " +
            "208 4 209 3 20a 4 20b 3 20c 4 20d 3 20e 4 20f 3 210 4 211 3 212 4 213 3 214 4 215 3 216 4 217 3 " +
            "218 4 219 3 21a 4 21b 3 21c 4 21d 3 21e 4 21f 3 220 4 221 3 222 4 223 3 224 4 225 3 226 4 227 3 " +
            "228 4 229 3 22a 4 22b 3 22c 4 22d 3 22e 4 22f 3 230 4 231 3 232 4 233 3 23a 4 23c 3 23d 4 23f 3 " +
            "241 4 242 3 243 4 247 3 248 4 249 3 24a 4 24b 3 24c 4 24d 3 24e 4 24f 3 294 5 296 3 2b9 5 2c0 3 " +
            "2c2 1 2c6 5 2d2 1 2e0 3 2e5 1 2ec 5 2ed 1 2ee 5 2ef 1 345 3 346 1 363 5 370 4 371 3 372 4 373 3 " +
            "374 5 375 1 376 4 377 3 378 1 37a 3 37e 1 37f 4 380 1 386 4 387 1 388 4 38b 1 38c 4 38d 1 38e 4 " +
            "390 3 391 4 3a2 1 3a3 4 3ac 3 3cf 4 3d0 3 3d2 5 3d5 3 3d8 4 3d9 3 3da 4 3db 3 3dc 4 3dd 3 3de 4 " +
            "3df 3 3e0 4 3e1 3 3e2 4 3e3 3 3e4 4 3e5 3 3e6 4 3e7 3 3e8 4 3e9 3 3ea 4 3eb 3 3ec 4 3ed 3 3ee 4 " +
            "3ef 3 3f4 4 3f5 3 3f6 1 3f7 4 3f8 3 3f9 4 3fb 3 3fd 4 430 3 460 4 461 3 462 4 463 3 464 4 465 3 " +
            "466 4 467 3 468 4 469 3 46a 4 46b 3 46c 4 46d 3 46e 4 46f 3 470 4 471 3 472 4 473 3 474 4 475 3 " +
            "476 4 477 3 478 4 479 3 47a 4 47b 3 47c 4 47d 3 47e 4 47f 3 480 4 481 3 482 1 48a 4 48b 3 48c 4 " +
            "48d 3 48e 4 48f 3 490 4 491 3 492 4 493 3 494 4 495 3 496 4 497 3 498 4 499 3 49a 4 49b 3 49c 4 " +
            "49d 3 49e 4 49f 3 4a0 4 4a1 3 4a2 4 4a3 3 4a4 4 4a5 3 4a6 4 4a7 3 4a8 4 4a9 3 4aa 4 4ab 3 4ac 4 " +
            "4ad 3 4ae 4 4af 3 4b0 4 4b1 3 4b2 4 4b3 3 4b4 4 4b5 3 4b6 4 4b7 3 4b8 4 4b9 3 4ba 4 4bb 3 4bc 4 " +
            "4bd 3 4be 4 4bf 3 4c0 4 4c2 3 4c3 4 4c4 3 4c5 4 4c6 3 4c7 4 4c8 3 4c9 4 4ca 3 4cb 4 4cc 3 4cd 4 " +
            "4ce 3 4d0 4 4d1 3 4d2 4 4d3 3 4d4 4 4d5 3 4d6 4 4d7 3 4d8 4 4d9 3 4da 4 4db 3 4dc 4 4dd 3 4de 4 " +
            "4df 3 4e0 4 4e1 3 4e2 4 4e3 3 4e4 4 4e5 3 4e6 4 4e7 3 4e8 4 4e9 3 4ea 4 4eb 3 4ec 4 4ed 3 4ee 4 " +
            "4ef 3 4f0 4 4f1 3 4f2 4 4f3 3 4f4 4 4f5 3 4f6 4 4f7 3 4f8 4 4f9 3 4fa 4 4fb 3 4fc 4 4fd 3 4fe 4 " +
            "4ff 3 500 4 501 3 502 4 503 3 504 4 505 3 506 4 507 3 508 4 509 3 50a 4 50b 3 50c 4 50d 3 50e 4 " +
            "50f 3 510 4 511 3 512 4 513 3 514 4 515 3 516 4 517 3 518 4 519 3 51a 4 51b 3 51c 4 51d 3 51e 4 " +
            "51f 3 520 4 521 3 522 4 523 3 524 4 525 3 526 4 527 3 528 4 529 3 52a 4 52b 3 52c 4 52d 3 52e 4 " +
            "52f 3 530 1 531 4 557 1 559 5 55a 1 560 3 589 1 5b0 5 5be 1 5bf 5 5c0 1 5c1 5 5c3 1 5c4 5 5c6 1 " +
            "5c7 5 5c8 1 5d0 5 5eb 1 5ef 5 5f3 1 610 5 61b 1 620 5 658 1 659 5 660 6 66a 1 66e 5 6d4 1 6d5 5 " +
            "6dd 1 6e1 5 6e9 1 6ed 5 6f0 6 6fa 5 6fd 1 6ff 5 700 1 710 5 740 1 74d 5 7b2 1 7c0 6 7ca 5 7eb 1 " +
            "7f4 5 7f6 1 7fa 5 7fb 1 800 5 818 1 81a 5 82d 1 840 5 859 1 860 5 86b 1 870 5 888 1 889 5 890 1 " +
            "897 5 898 1 8a0 5 8ca 1 8d4 5 8e0 1 8e3 5 8ea 1 8f0 5 93c 1 93d 5 94d 1 94e 5 951 1 955 5 964 1 " +
            "966 6 970 1 971 5 984 1 985 5 98d 1 98f 5 991 1 993 5 9a9 1 9aa 5 9b1 1 9b2 5 9b3 1 9b6 5 9ba 1 " +
            "9bd 5 9c5 1 9c7 5 9c9 1 9cb 5 9cd 1 9ce 5 9cf 1 9d7 5 9d8 1 9dc 5 9de 1 9df 5 9e4 1 9e6 6 9f0 5 " +
            "9f2 1 9f4 6 9fa 1 9fc 5 9fd 1 a01 5 a04 1 a05 5 a0b 1 a0f 5 a11 1 a13 5 a29 1 a2a 5 a31 1 a32 5 " +
            "a34 1 a35 5 a37 1 a38 5 a3a 1 a3e 5 a43 1 a47 5 a49 1 a4b 5 a4d 1 a51 5 a52 1 a59 5 a5d 1 a5e 5 " +
            "a5f 1 a66 6 a70 5 a76 1 a81 5 a84 1 a85 5 a8e 1 a8f 5 a92 1 a93 5 aa9 1 aaa 5 ab1 1 ab2 5 ab4 1 " +
            "ab5 5 aba 1 abd 5 ac6 1 ac7 5 aca 1 acb 5 acd 1 ad0 5 ad1 1 ae0 5 ae4 1 ae6 6 af0 1 af9 5 afd 1 " +
            "b01 5 b04 1 b05 5 b0d 1 b0f 5 b11 1 b13 5 b29 1 b2a 5 b31 1 b32 5 b34 1 b35 5 b3a 1 b3d 5 b45 1 " +
            "b47 5 b49 1 b4b 5 b4d 1 b56 5 b58 1 b5c 5 b5e 1 b5f 5 b64 1 b66 6 b70 1 b71 5 b72 6 b78 1 b82 5 " +
            "b84 1 b85 5 b8b 1 b8e 5 b91 1 b92 5 b96 1 b99 5 b9b 1 b9c 5 b9d 1 b9e 5 ba0 1 ba3 5 ba5 1 ba8 5 " +
            "bab 1 bae 5 bba 1 bbe 5 bc3 1 bc6 5 bc9 1 bca 5 bcd 1 bd0 5 bd1 1 bd7 5 bd8 1 be6 6 bf3 1 c00 5 " +
            "c0d 1 c0e 5 c11 1 c12 5 c29 1 c2a 5 c3a 1 c3d 5 c45 1 c46 5 c49 1 c4a 5 c4d 1 c55 5 c57 1 c58 5 " +
            "c5b 1 c5c 5 c5e 1 c60 5 c64 1 c66 6 c70 1 c78 6 c7f 1 c80 5 c84 1 c85 5 c8d 1 c8e 5 c91 1 c92 5 " +
            "ca9 1 caa 5 cb4 1 cb5 5 cba 1 cbd 5 cc5 1 cc6 5 cc9 1 cca 5 ccd 1 cd5 5 cd7 1 cdc 5 cdf 1 ce0 5 " +
            "ce4 1 ce6 6 cf0 1 cf1 5 cf4 1 d00 5 d0d 1 d0e 5 d11 1 d12 5 d3b 1 d3d 5 d45 1 d46 5 d49 1 d4a 5 " +
            "d4d 1 d4e 5 d4f 1 d54 5 d58 6 d5f 5 d64 1 d66 6 d79 1 d7a 5 d80 1 d81 5 d84 1 d85 5 d97 1 d9a 5 " +
            "db2 1 db3 5 dbc 1 dbd 5 dbe 1 dc0 5 dc7 1 dcf 5 dd5 1 dd6 5 dd7 1 dd8 5 de0 1 de6 6 df0 1 df2 5 " +
            "df4 1 e01 5 e3b 1 e40 5 e47 1 e4d 5 e4e 1 e50 6 e5a 1 e81 5 e83 1 e84 5 e85 1 e86 5 e8b 1 e8c 5 " +
            "ea4 1 ea5 5 ea6 1 ea7 5 eba 1 ebb 5 ebe 1 ec0 5 ec5 1 ec6 5 ec7 1 ecd 5 ece 1 ed0 6 eda 1 edc 5 " +
            "ee0 1 f00 5 f01 1 f20 6 f34 1 f40 5 f48 1 f49 5 f6d 1 f71 5 f84 1 f88 5 f98 1 f99 5 fbd 1 1000 5 " +
            "1037 1 1038 5 1039 1 103b 5 1040 6 104a 1 1050 5 1090 6 109a 5 109e 1 10a0 4 10c6 1 10c7 4 10c8 1 10cd 4 10ce 1 " +
            "10d0 3 10fb 1 10fc 3 1100 5 1249 1 124a 5 124e 1 1250 5 1257 1 1258 5 1259 1 125a 5 125e 1 1260 5 1289 1 128a 5 " +
            "128e 1 1290 5 12b1 1 12b2 5 12b6 1 12b8 5 12bf 1 12c0 5 12c1 1 12c2 5 12c6 1 12c8 5 12d7 1 12d8 5 1311 1 1312 5 " +
            "1316 1 1318 5 135b 1 1369 6 137d 1 1380 5 1390 1 13a0 5 13f6 1 13f8 3 13fe 1 1401 5 166d 1 166f 5 1680 0 1681 5 " +
            "169b 1 16a0 5 16eb 1 16ee 6 16f1 5 16f9 1 1700 5 1714 1 171f 5 1734 1 1740 5 1754 1 1760 5 176d 1 176e 5 1771 1 " +
            "1772 5 1774 1 1780 5 17b4 1 17b6 5 17c9 1 17d7 5 17d8 1 17dc 5 17dd 1 17e0 6 17ea 1 17f0 6 17fa 1 1810 6 181a 1 " +
            "1820 5 1879 1 1880 5 18ab 1 18b0 5 18f6 1 1900 5 191f 1 1920 5 192c 1 1930 5 1939 1 1946 6 1950 5 196e 1 1970 5 " +
            "1975 1 1980 5 19ac 1 19b0 5 19ca 1 19d0 6 19db 1 1a00 5 1a1c 1 1a20 5 1a5f 1 1a61 5 1a75 1 1a80 6 1a8a 1 1a90 6 " +
            "1a9a 1 1aa7 5 1aa8 1 1abf 5 1ac1 1 1acc 5 1acf 1 1b00 5 1b34 1 1b35 5 1b44 1 1b45 5 1b4d 1 1b50 6 1b5a 1 1b80 5 " +
            "1baa 1 1bac 5 1bb0 6 1bba 5 1be6 1 1be7 5 1bf2 1 1c00 5 1c37 1 1c40 6 1c4a 1 1c4d 5 1c50 6 1c5a 5 1c7e 1 1c80 3 " +
            "1c89 5 1c8a 3 1c8b 1 1c90 4 1cbb 1 1cbd 4 1cc0 1 1ce9 5 1ced 1 1cee 5 1cf4 1 1cf5 5 1cf7 1 1cfa 5 1cfb 1 1d00 3 " +
            "1dc0 1 1dd3 5 1df5 1 1e00 4 1e01 3 1e02 4 1e03 3 1e04 4 1e05 3 1e06 4 1e07 3 1e08 4 1e09 3 1e0a 4 1e0b 3 1e0c 4 " +
            "1e0d 3 1e0e 4 1e0f 3 1e10 4 1e11 3 1e12 4 1e13 3 1e14 4 1e15 3 1e16 4 1e17 3 1e18 4 1e19 3 1e1a 4 1e1b 3 1e1c 4 " +
            "1e1d 3 1e1e 4 1e1f 3 1e20 4 1e21 3 1e22 4 1e23 3 1e24 4 1e25 3 1e26 4 1e27 3 1e28 4 1e29 3 1e2a 4 1e2b 3 1e2c 4 " +
            "1e2d 3 1e2e 4 1e2f 3 1e30 4 1e31 3 1e32 4 1e33 3 1e34 4 1e35 3 1e36 4 1e37 3 1e38 4 1e39 3 1e3a 4 1e3b 3 1e3c 4 " +
            "1e3d 3 1e3e 4 1e3f 3 1e40 4 1e41 3 1e42 4 1e43 3 1e44 4 1e45 3 1e46 4 1e47 3 1e48 4 1e49 3 1e4a 4 1e4b 3 1e4c 4 " +
            "1e4d 3 1e4e 4 1e4f 3 1e50 4 1e51 3 1e52 4 1e53 3 1e54 4 1e55 3 1e56 4 1e57 3 1e58 4 1e59 3 1e5a 4 1e5b 3 1e5c 4 " +
            "1e5d 3 1e5e 4 1e5f 3 1e60 4 1e61 3 1e62 4 1e63 3 1e64 4 1e65 3 1e66 4 1e67 3 1e68 4 1e69 3 1e6a 4 1e6b 3 1e6c 4 " +
            "1e6d 3 1e6e 4 1e6f 3 1e70 4 1e71 3 1e72 4 1e73 3 1e74 4 1e75 3 1e76 4 1e77 3 1e78 4 1e79 3 1e7a 4 1e7b 3 1e7c 4 " +
            "1e7d 3 1e7e 4 1e7f 3 1e80 4 1e81 3 1e82 4 1e83 3 1e84 4 1e85 3 1e86 4 1e87 3 1e88 4 1e89 3 1e8a 4 1e8b 3 1e8c 4 " +
            "1e8d 3 1e8e 4 1e8f 3 1e90 4 1e91 3 1e92 4 1e93 3 1e94 4 1e95 3 1e9e 4 1e9f 3 1ea0 4 1ea1 3 1ea2 4 1ea3 3 1ea4 4 " +
            "1ea5 3 1ea6 4 1ea7 3 1ea8 4 1ea9 3 1eaa 4 1eab 3 1eac 4 1ead 3 1eae 4 1eaf 3 1eb0 4 1eb1 3 1eb2 4 1eb3 3 1eb4 4 " +
            "1eb5 3 1eb6 4 1eb7 3 1eb8 4 1eb9 3 1eba 4 1ebb 3 1ebc 4 1ebd 3 1ebe 4 1ebf 3 1ec0 4 1ec1 3 1ec2 4 1ec3 3 1ec4 4 " +
            "1ec5 3 1ec6 4 1ec7 3 1ec8 4 1ec9 3 1eca 4 1ecb 3 1ecc 4 1ecd 3 1ece 4 1ecf 3 1ed0 4 1ed1 3 1ed2 4 1ed3 3 1ed4 4 " +
            "1ed5 3 1ed6 4 1ed7 3 1ed8 4 1ed9 3 1eda 4 1edb 3 1edc 4 1edd 3 1ede 4 1edf 3 1ee0 4 1ee1 3 1ee2 4 1ee3 3 1ee4 4 " +
            "1ee5 3 1ee6 4 1ee7 3 1ee8 4 1ee9 3 1eea 4 1eeb 3 1eec 4 1eed 3 1eee 4 1eef 3 1ef0 4 1ef1 3 1ef2 4 1ef3 3 1ef4 4 " +
            "1ef5 3 1ef6 4 1ef7 3 1ef8 4 1ef9 3 1efa 4 1efb 3 1efc 4 1efd 3 1efe 4 1eff 3 1f08 4 1f10 3 1f16 1 1f18 4 1f1e 1 " +
            "1f20 3 1f28 4 1f30 3 1f38 4 1f40 3 1f46 1 1f48 4 1f4e 1 1f50 3 1f58 1 1f59 4 1f5a 1 1f5b 4 1f5c 1 1f5d 4 1f5e 1 " +
            "1f5f 4 1f60 3 1f68 4 1f70 3 1f7e 1 1f80 3 1f88 4 1f90 3 1f98 4 1fa0 3 1fa8 4 1fb0 3 1fb5 1 1fb6 3 1fb8 4 1fbd 1 " +
            "1fbe 3 1fbf 1 1fc2 3 1fc5 1 1fc6 3 1fc8 4 1fcd 1 1fd0 3 1fd4 1 1fd6 3 1fd8 4 1fdc 1 1fe0 3 1fe8 4 1fed 1 1ff2 3 " +
            "1ff5 1 1ff6 3 1ff8 4 1ffd 1 2000 0 200b 1 2028 0 202a 1 202f 0 2030 1 205f 0 2060 1 2070 6 2071 3 2072 1 2074 6 " +
            "207a 1 207f 3 2080 6 208a 1 2090 3 209d 1 2102 5 2103 1 2107 5 2108 1 210a 3 210b 5 210e 3 2110 5 2113 3 2114 1 " +
            "2115 5 2116 1 2119 5 211e 1 2124 5 2125 1 2126 4 2127 1 2128 5 2129 1 212a 4 212c 5 212e 1 212f 3 2130 5 2132 4 " +
            "2133 5 2134 3 2135 5 2139 3 213a 1 213c 3 213e 5 2140 1 2145 5 2146 3 214a 1 214e 3 214f 1 2150 6 2160 4 2170 3 " +
            "2180 6 2183 4 2184 3 2185 6 218a 1 2460 6 249c 1 24b6 4 24d0 3 24ea 6 2500 1 2776 6 2794 1 2c00 4 2c30 3 2c60 4 " +
            "2c61 3 2c62 4 2c65 3 2c67 4 2c68 3 2c69 4 2c6a 3 2c6b 4 2c6c 3 2c6d 4 2c71 3 2c72 4 2c73 3 2c75 4 2c76 3 2c7e 4 " +
            "2c81 3 2c82 4 2c83 3 2c84 4 2c85 3 2c86 4 2c87 3 2c88 4 2c89 3 2c8a 4 2c8b 3 2c8c 4 2c8d 3 2c8e 4 2c8f 3 2c90 4 " +
            "2c91 3 2c92 4 2c93 3 2c94 4 2c95 3 2c96 4 2c97 3 2c98 4 2c99 3 2c9a 4 2c9b 3 2c9c 4 2c9d 3 2c9e 4 2c9f 3 2ca0 4 " +
            "2ca1 3 2ca2 4 2ca3 3 2ca4 4 2ca5 3 2ca6 4 2ca7 3 2ca8 4 2ca9 3 2caa 4 2cab 3 2cac 4 2cad 3 2cae 4 2caf 3 2cb0 4 " +
            "2cb1 3 2cb2 4 2cb3 3 2cb4 4 2cb5 3 2cb6 4 2cb7 3 2cb8 4 2cb9 3 2cba 4 2cbb 3 2cbc 4 2cbd 3 2cbe 4 2cbf 3 2cc0 4 " +
            "2cc1 3 2cc2 4 2cc3 3 2cc4 4 2cc5 3 2cc6 4 2cc7 3 2cc8 4 2cc9 3 2cca 4 2ccb 3 2ccc 4 2ccd 3 2cce 4 2ccf 3 2cd0 4 " +
            "2cd1 3 2cd2 4 2cd3 3 2cd4 4 2cd5 3 2cd6 4 2cd7 3 2cd8 4 2cd9 3 2cda 4 2cdb 3 2cdc 4 2cdd 3 2cde 4 2cdf 3 2ce0 4 " +
            "2ce1 3 2ce2 4 2ce3 3 2ce5 1 2ceb 4 2cec 3 2ced 4 2cee 3 2cef 1 2cf2 4 2cf3 3 2cf4 1 2cfd 6 2cfe 1 2d00 3 2d26 1 " +
            "2d27 3 2d28 1 2d2d 3 2d2e 1 2d30 5 2d68 1 2d6f 5 2d70 1 2d80 5 2d97 1 2da0 5 2da7 1 2da8 5 2daf 1 2db0 5 2db7 1 " +
            "2db8 5 2dbf 1 2dc0 5 2dc7 1 2dc8 5 2dcf 1 2dd0 5 2dd7 1 2dd8 5 2ddf 1 2de0 5 2e00 1 2e2f 5 2e30 1 3000 0 3001 1 " +
            "3005 5 3007 6 3008 1 3021 6 302a 1 3031 5 3036 1 3038 6 303b 5 303d 1 3041 5 3097 1 309d 5 30a0 1 30a1 5 30fb 1 " +
            "30fc 5 3100 1 3105 5 3130 1 3131 5 318f 1 3192 6 3196 1 31a0 5 31c0 1 31f0 5 3200 1 3220 6 322a 1 3248 6 3250 1 " +
            "3251 6 3260 1 3280 6 328a 1 32b1 6 32c0 1 3400 5 4dc0 1 4e00 5 a48d 1 a4d0 5 a4fe 1 a500 5 a60d 1 a610 5 a620 6 " +
            "a62a 5 a62c 1 a640 4 a641 3 a642 4 a643 3 a644 4 a645 3 a646 4 a647 3 a648 4 a649 3 a64a 4 a64b 3 a64c 4 a64d 3 " +
            "a64e 4 a64f 3 a650 4 a651 3 a652 4 a653 3 a654 4 a655 3 a656 4 a657 3 a658 4 a659 3 a65a 4 a65b 3 a65c 4 a65d 3 " +
            "a65e 4 a65f 3 a660 4 a661 3 a662 4 a663 3 a664 4 a665 3 a666 4 a667 3 a668 4 a669 3 a66a 4 a66b 3 a66c 4 a66d 3 " +
            "a66e 5 a66f 1 a674 5 a67c 1 a67f 5 a680 4 a681 3 a682 4 a683 3 a684 4 a685 3 a686 4 a687 3 a688 4 a689 3 a68a 4 " +
            "a68b 3 a68c 4 a68d 3 a68e 4 a68f 3 a690 4 a691 3 a692 4 a693 3 a694 4 a695 3 a696 4 a697 3 a698 4 a699 3 a69a 4 " +
            "a69b 3 a69e 5 a6e6 6 a6f0 1 a717 5 a720 1 a722 4 a723 3 a724 4 a725 3 a726 4 a727 3 a728 4 a729 3 a72a 4 a72b 3 " +
            "a72c 4 a72d 3 a72e 4 a72f 3 a732 4 a733 3 a734 4 a735 3 a736 4 a737 3 a738 4 a739 3 a73a 4 a73b 3 a73c 4 a73d 3 " +
            "a73e 4 a73f 3 a740 4 a741 3 a742 4 a743 3 a744 4 a745 3 a746 4 a747 3 a748 4 a749 3 a74a 4 a74b 3 a74c 4 a74d 3 " +
            "a74e 4 a74f 3 a750 4 a751 3 a752 4 a753 3 a754 4 a755 3 a756 4 a757 3 a758 4 a759 3 a75a 4 a75b 3 a75c 4 a75d 3 " +
            "a75e 4 a75f 3 a760 4 a761 3 a762 4 a763 3 a764 4 a765 3 a766 4 a767 3 a768 4 a769 3 a76a 4 a76b 3 a76c 4 a76d 3 " +
            "a76e 4 a76f 3 a779 4 a77a 3 a77b 4 a77c 3 a77d 4 a77f 3 a780 4 a781 3 a782 4 a783 3 a784 4 a785 3 a786 4 a787 3 " +
            "a788 5 a789 1 a78b 4 a78c 3 a78d 4 a78e 3 a78f 5 a790 4 a791 3 a792 4 a793 3 a796 4 a797 3 a798 4 a799 3 a79a 4 " +
            "a79b 3 a79c 4 a79d 3 a79e 4 a79f 3 a7a0 4 a7a1 3 a7a2 4 a7a3 3 a7a4 4 a7a5 3 a7a6 4 a7a7 3 a7a8 4 a7a9 3 a7aa 4 " +
            "a7af 3 a7b0 4 a7b5 3 a7b6 4 a7b7 3 a7b8 4 a7b9 3 a7ba 4 a7bb 3 a7bc 4 a7bd 3 a7be 4 a7bf 3 a7c0 4 a7c1 3 a7c2 4 " +
            "a7c3 3 a7c4 4 a7c8 3 a7c9 4 a7ca 3 a7cb 5 a7cd 3 a7ce 5 a7cf 3 a7d0 4 a7d1 3 a7d2 5 a7d3 3 a7d4 5 a7d5 3 a7d6 4 " +
            "a7d7 3 a7d8 4 a7d9 3 a7da 5 a7db 3 a7dc 5 a7dd 1 a7f1 3 a7f5 4 a7f6 3 a7f7 5 a7f8 3 a7fb 5 a806 1 a807 5 a828 1 " +
            "a830 6 a836 1 a840 5 a874 1 a880 5 a8c4 1 a8c5 5 a8c6 1 a8d0 6 a8da 1 a8f2 5 a8f8 1 a8fb 5 a8fc 1 a8fd 5 a900 6 " +
            "a90a 5 a92b 1 a930 5 a953 1 a960 5 a97d 1 a980 5 a9b3 1 a9b4 5 a9c0 1 a9cf 5 a9d0 6 a9da 1 a9e0 5 a9f0 6 a9fa 5 " +
            "a9ff 1 aa00 5 aa37 1 aa40 5 aa4e 1 aa50 6 aa5a 1 aa60 5 aa77 1 aa7a 5 aabf 1 aac0 5 aac1 1 aac2 5 aac3 1 aadb 5 " +
            "aade 1 aae0 5 aaf0 1 aaf2 5 aaf6 1 ab01 5 ab07 1 ab09 5 ab0f 1 ab11 5 ab17 1 ab20 5 ab27 1 ab28 5 ab2f 1 ab30 3 " +
            "ab5b 1 ab5c 3 ab6a 1 ab70 3 abc0 5 abeb 1 abf0 6 abfa 1 ac00 5 d7a4 1 d7b0 5 d7c7 1 d7cb 5 d7fc 1 f900 5 fa6e 1 " +
            "fa70 5 fada 1 fb00 3 fb07 1 fb13 3 fb18 1 fb1d 5 fb29 1 fb2a 5 fb37 1 fb38 5 fb3d 1 fb3e 5 fb3f 1 fb40 5 fb42 1 " +
            "fb43 5 fb45 1 fb46 5 fbb2 1 fbd3 5 fd3e 1 fd50 5 fd90 1 fd92 5 fdc8 1 fdf0 5 fdfc 1 fe70 5 fe75 1 fe76 5 fefd 1 " +
            "ff10 6 ff1a 1 ff21 4 ff3b 1 ff41 3 ff5b 1 ff66 5 ffbf 1 ffc2 5 ffc8 1 ffca 5 ffd0 1 ffd2 5 ffd8 1 ffda 5 ffdd 1 " +
            "10000 5 1000c 1 1000d 5 10027 1 10028 5 1003b 1 1003c 5 1003e 1 1003f 5 1004e 1 10050 5 1005e 1 10080 5 100fb 1 10107 6 10134 1 " +
            "10140 6 10179 1 1018a 6 1018c 1 10280 5 1029d 1 102a0 5 102d1 1 102e1 6 102fc 1 10300 5 10320 6 10324 1 1032d 5 10341 6 10342 5 " +
            "1034a 6 1034b 1 10350 5 1037b 1 10380 5 1039e 1 103a0 5 103c4 1 103c8 5 103d0 1 103d1 6 103d6 1 10400 4 10428 3 10450 5 1049e 1 " +
            "104a0 6 104aa 1 104b0 4 104d4 1 104d8 3 104fc 1 10500 5 10528 1 10530 5 10564 1 10570 4 1057b 1 1057c 4 1058b 1 1058c 4 10593 1 " +
            "10594 4 10596 1 10597 3 105a2 1 105a3 3 105b2 1 105b3 3 105ba 1 105bb 3 105bd 1 105c0 5 105f4 1 10600 5 10737 1 10740 5 10756 1 " +
            "10760 5 10768 1 10780 3 10781 5 10783 3 10786 1 10787 3 107b1 1 107b2 3 107bb 1 10800 5 10806 1 10808 5 10809 1 1080a 5 10836 1 " +
            "10837 5 10839 1 1083c 5 1083d 1 1083f 5 10856 1 10858 6 10860 5 10877 1 10879 6 10880 5 1089f 1 108a7 6 108b0 1 108e0 5 108f3 1 " +
            "108f4 5 108f6 1 108fb 6 10900 5 10916 6 1091c 1 10920 5 1093a 1 10940 5 1095a 1 10980 5 109b8 1 109bc 6 109be 5 109c0 6 109d0 1 " +
            "109d2 6 10a00 5 10a04 1 10a05 5 10a07 1 10a0c 5 10a14 1 10a15 5 10a18 1 10a19 5 10a36 1 10a40 6 10a49 1 10a60 5 10a7d 6 10a7f 1 " +
            "10a80 5 10a9d 6 10aa0 1 10ac0 5 10ac8 1 10ac9 5 10ae5 1 10aeb 6 10af0 1 10b00 5 10b36 1 10b40 5 10b56 1 10b58 6 10b60 5 10b73 1 " +
            "10b78 6 10b80 5 10b92 1 10ba9 6 10bb0 1 10c00 5 10c49 1 10c80 4 10cb3 1 10cc0 3 10cf3 1 10cfa 6 10d00 5 10d28 1 10d30 6 10d3a 1 " +
            "10d40 6 10d4a 5 10d66 1 10d69 5 10d6a 1 10d6f 5 10d70 3 10d86 1 10e60 6 10e7f 1 10e80 5 10eaa 1 10eab 5 10ead 1 10eb0 5 10eb2 1 " +
            "10ec2 5 10ec8 1 10efa 5 10efd 1 10f00 5 10f1d 6 10f27 5 10f28 1 10f30 5 10f46 1 10f51 6 10f55 1 10f70 5 10f82 1 10fb0 5 10fc5 6 " +
            "10fcc 1 10fe0 5 10ff7 1 11000 5 11046 1 11052 6 11070 1 11071 5 11076 1 11080 5 110b9 1 110c2 5 110c3 1 110d0 5 110e9 1 110f0 6 " +
            "110fa 1 11100 5 11133 1 11136 6 11140 1 11144 5 11148 1 11150 5 11173 1 11176 5 11177 1 11180 5 111c0 1 111c1 5 111c5 1 111ce 5 " +
            "111d0 6 111da 5 111db 1 111dc 5 111dd 1 111e1 6 111f5 1 11200 5 11212 1 11213 5 11235 1 11237 5 11238 1 1123e 5 11242 1 11280 5 " +
            "11287 1 11288 5 11289 1 1128a 5 1128e 1 1128f 5 1129e 1 1129f 5 112a9 1 112b0 5 112e9 1 112f0 6 112fa 1 11300 5 11304 1 11305 5 " +
            "1130d 1 1130f 5 11311 1 11313 5 11329 1 1132a 5 11331 1 11332 5 11334 1 11335 5 1133a 1 1133d 5 11345 1 11347 5 11349 1 1134b 5 " +
            "1134d 1 11350 5 11351 1 11357 5 11358 1 1135d 5 11364 1 11380 5 1138a 1 1138b 5 1138c 1 1138e 5 1138f 1 11390 5 113b6 1 113b7 5 " +
            "113c1 1 113c2 5 113c3 1 113c5 5 113c6 1 113c7 5 113cb 1 113cc 5 113ce 1 113d1 5 113d2 1 113d3 5 113d4 1 11400 5 11442 1 11443 5 " +
            "11446 1 11447 5 1144b 1 11450 6 1145a 1 1145f 5 11462 1 11480 5 114c2 1 114c4 5 114c6 1 114c7 5 114c8 1 114d0 6 114da 1 11580 5 " +
            "115b6 1 115b8 5 115bf 1 115d8 5 115de 1 11600 5 1163f 1 11640 5 11641 1 11644 5 11645 1 11650 6 1165a 1 11680 5 116b6 1 116b8 5 " +
            "116b9 1 116c0 6 116ca 1 116d0 6 116e4 1 11700 5 1171b 1 1171d 5 1172b 1 11730 6 1173c 1 11740 5 11747 1 11800 5 11839 1 118a0 4 " +
            "118c0 3 118e0 6 118f3 1 118ff 5 11907 1 11909 5 1190a 1 1190c 5 11914 1 11915 5 11917 1 11918 5 11936 1 11937 5 11939 1 1193b 5 " +
            "1193d 1 1193f 5 11943 1 11950 6 1195a 1 119a0 5 119a8 1 119aa 5 119d8 1 119da 5 119e0 1 119e1 5 119e2 1 119e3 5 119e5 1 11a00 5 " +
            "11a33 1 11a35 5 11a3f 1 11a50 5 11a98 1 11a9d 5 11a9e 1 11ab0 5 11af9 1 11b60 5 11b68 1 11bc0 5 11be1 1 11bf0 6 11bfa 1 11c00 5 " +
            "11c09 1 11c0a 5 11c37 1 11c38 5 11c3f 1 11c40 5 11c41 1 11c50 6 11c6d 1 11c72 5 11c90 1 11c92 5 11ca8 1 11ca9 5 11cb7 1 11d00 5 " +
            "11d07 1 11d08 5 11d0a 1 11d0b 5 11d37 1 11d3a 5 11d3b 1 11d3c 5 11d3e 1 11d3f 5 11d42 1 11d43 5 11d44 1 11d46 5 11d48 1 11d50 6 " +
            "11d5a 1 11d60 5 11d66 1 11d67 5 11d69 1 11d6a 5 11d8f 1 11d90 5 11d92 1 11d93 5 11d97 1 11d98 5 11d99 1 11da0 6 11daa 1 11db0 5 " +
            "11ddc 1 11de0 6 11dea 1 11ee0 5 11ef7 1 11f00 5 11f11 1 11f12 5 11f3b 1 11f3e 5 11f41 1 11f50 6 11f5a 1 11fb0 5 11fb1 1 11fc0 6 " +
            "11fd5 1 12000 5 1239a 1 12400 6 1246f 1 12480 5 12544 1 12f90 5 12ff1 1 13000 5 13430 1 13441 5 13447 1 13460 5 143fb 1 14400 5 " +
            "14647 1 16100 5 1612f 1 16130 6 1613a 1 16800 5 16a39 1 16a40 5 16a5f 1 16a60 6 16a6a 1 16a70 5 16abf 1 16ac0 6 16aca 1 16ad0 5 " +
            "16aee 1 16b00 5 16b30 1 16b40 5 16b44 1 16b50 6 16b5a 1 16b5b 6 16b62 1 16b63 5 16b78 1 16b7d 5 16b90 1 16d40 5 16d6d 1 16d70 6 " +
            "16d7a 1 16e40 4 16e60 3 16e80 6 16e97 1 16ea0 5 16eb9 1 16ebb 3 16ed4 1 16f00 5 16f4b 1 16f4f 5 16f88 1 16f8f 5 16fa0 1 16fe0 5 " +
            "16fe2 1 16fe3 5 16fe4 1 16ff0 5 16ff4 6 16ff7 1 17000 5 18cd6 1 18cff 5 18d1f 1 18d80 5 18df3 1 1aff0 5 1aff4 1 1aff5 5 1affc 1 " +
            "1affd 5 1afff 1 1b000 5 1b123 1 1b132 5 1b133 1 1b150 5 1b153 1 1b155 5 1b156 1 1b164 5 1b168 1 1b170 5 1b2fc 1 1bc00 5 1bc6b 1 " +
            "1bc70 5 1bc7d 1 1bc80 5 1bc89 1 1bc90 5 1bc9a 1 1bc9e 5 1bc9f 1 1ccf0 6 1ccfa 1 1d2c0 6 1d2d4 1 1d2e0 6 1d2f4 1 1d360 6 1d379 1 " +
            "1d400 5 1d41a 3 1d434 5 1d44e 3 1d455 1 1d456 3 1d468 5 1d482 3 1d49c 5 1d49d 1 1d49e 5 1d4a0 1 1d4a2 5 1d4a3 1 1d4a5 5 1d4a7 1 " +
            "1d4a9 5 1d4ad 1 1d4ae 5 1d4b6 3 1d4ba 1 1d4bb 3 1d4bc 1 1d4bd 3 1d4c4 1 1d4c5 3 1d4d0 5 1d4ea 3 1d504 5 1d506 1 1d507 5 1d50b 1 " +
            "1d50d 5 1d515 1 1d516 5 1d51d 1 1d51e 3 1d538 5 1d53a 1 1d53b 5 1d53f 1 1d540 5 1d545 1 1d546 5 1d547 1 1d54a 5 1d551 1 1d552 3 " +
            "1d56c 5 1d586 3 1d5a0 5 1d5ba 3 1d5d4 5 1d5ee 3 1d608 5 1d622 3 1d63c 5 1d656 3 1d670 5 1d68a 3 1d6a6 1 1d6a8 5 1d6c1 1 1d6c2 3 " +
            "1d6db 1 1d6dc 3 1d6e2 5 1d6fb 1 1d6fc 3 1d715 1 1d716 3 1d71c 5 1d735 1 1d736 3 1d74f 1 1d750 3 1d756 5 1d76f 1 1d770 3 1d789 1 " +
            "1d78a 3 1d790 5 1d7a9 1 1d7aa 3 1d7c3 1 1d7c4 3 1d7ca 5 1d7cb 3 1d7cc 1 1d7ce 6 1d800 1 1df00 3 1df0a 5 1df0b 3 1df1f 1 1df25 3 " +
            "1df2b 1 1e000 5 1e007 1 1e008 5 1e019 1 1e01b 5 1e022 1 1e023 5 1e025 1 1e026 5 1e02b 1 1e030 3 1e06e 1 1e08f 5 1e090 1 1e100 5 " +
            "1e12d 1 1e137 5 1e13e 1 1e140 6 1e14a 1 1e14e 5 1e14f 1 1e290 5 1e2ae 1 1e2c0 5 1e2ec 1 1e2f0 6 1e2fa 1 1e4d0 5 1e4ec 1 1e4f0 6 " +
            "1e4fa 1 1e5d0 5 1e5ee 1 1e5f0 5 1e5f1 6 1e5fb 1 1e6c0 5 1e6df 1 1e6e0 5 1e6f6 1 1e6fe 5 1e700 1 1e7e0 5 1e7e7 1 1e7e8 5 1e7ec 1 " +
            "1e7ed 5 1e7ef 1 1e7f0 5 1e7ff 1 1e800 5 1e8c5 1 1e8c7 6 1e8d0 1 1e900 4 1e922 3 1e944 1 1e947 5 1e948 1 1e94b 5 1e94c 1 1e950 6 " +
            "1e95a 1 1ec71 6 1ecac 1 1ecad 6 1ecb0 1 1ecb1 6 1ecb5 1 1ed01 6 1ed2e 1 1ed2f 6 1ed3e 1 1ee00 5 1ee04 1 1ee05 5 1ee20 1 1ee21 5 " +
            "1ee23 1 1ee24 5 1ee25 1 1ee27 5 1ee28 1 1ee29 5 1ee33 1 1ee34 5 1ee38 1 1ee39 5 1ee3a 1 1ee3b 5 1ee3c 1 1ee42 5 1ee43 1 1ee47 5 " +
            "1ee48 1 1ee49 5 1ee4a 1 1ee4b 5 1ee4c 1 1ee4d 5 1ee50 1 1ee51 5 1ee53 1 1ee54 5 1ee55 1 1ee57 5 1ee58 1 1ee59 5 1ee5a 1 1ee5b 5 " +
            "1ee5c 1 1ee5d 5 1ee5e 1 1ee5f 5 1ee60 1 1ee61 5 1ee63 1 1ee64 5 1ee65 1 1ee67 5 1ee6b 1 1ee6c 5 1ee73 1 1ee74 5 1ee78 1 1ee79 5 " +
            "1ee7d 1 1ee7e 5 1ee7f 1 1ee80 5 1ee8a 1 1ee8b 5 1ee9c 1 1eea1 5 1eea4 1 1eea5 5 1eeaa 1 1eeab 5 1eebc 1 1f100 6 1f10d 1 1f130 5 " +
            "1f14a 1 1f150 5 1f16a 1 1f170 5 1f18a 1 1fbf0 6 1fbfa 1 20000 5 2a6e0 1 2a700 5 2b81e 1 2b820 5 2ceae 1 2ceb0 5 2ebe1 1 2ebf0 5 " +
            "2ee5e 1 2f800 5 2fa1e 1 30000 5 3134b 1 31350 5 3347a 1");
}
