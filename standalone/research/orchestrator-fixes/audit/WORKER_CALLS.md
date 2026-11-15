# Every cold Real11 worker-call slot

208 dispatches:172 main and36 spatial. Session assignment is from the saved main-branch/probe evaluation; rows are grouped by program, not claimed to be globally timestamp ordered. See [the main audit](CANDIDATE_AUDIT.md) for all constraints, decisions, validation and pruning analysis. A spatial evaluation may have a saved winning witness even when individual call returns were not saved. Only a one-call evaluation with the saved winner identifies its individual problem hash.

| Call | Type / session | Parent / horizon | Jump / run inputs | Fixed constraints | Problem hash | Worker return / seconds | Post-check evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P01-B001-C1 | main / 1 | P01 / 10 | 0 / - | P01-B001 | b3c1fcd4837fbc61e34c84f638f1f59040226ee0683c12cc8be8ef568e212b4d | feasible / 0.1398 | accepted_prefix |
| P02-B001-C1 | main / 1 | P02 / 10 | 8 / 0..7 | P02-B001 | 1e0a365f8b95d8bb6513da1ccca1984bbfa4761c30385663eec3b775eaed76bb | feasible / 0.1996 | accepted_prefix |
| P03-B001-C1 | main / 1 | P03 / 28 | 0,12,21 / - | P03-B001 | 3c778e289d9e0c769bf324fa195a869459c774ff89db470bf025c5ba52927a30 | feasible / 0.1504 | corridor_signature |
| P03-B002-C1 | main / 1 | P03 / 28 | 0,12,21 / - | P03-B002 | 0405f667830fb4c27f993b1bdea5815a1cd4eea981705e2d3f2f940d35d6d26b | feasible / 1.5756 | accepted_prefix |
| P04-B001-C1 | main / 1 | P04 / 28 | 0,12,21 / - | P04-B001 | 6a4c4d9e1e9304ff93e1a4eead3ac2fd073f963353b56037b0da258375d4290a | feasible / 0.1492 | corridor_signature |
| P04-B002-C1 | main / 1 | P04 / 28 | 0,12,21 / - | P04-B002 | e30641248ac79ee36a65001ca9e858b9093e11bcf53271ead38062bfc881fb23 | feasible / 1.7761 | accepted_prefix |
| P05-B001-C1 | main / 1 | P05 / 28 | 0,12,21 / - | P05-B001 | 619ed0402c852a896d89c53e3dd9221d54e2f50f8c126a7f8747674f4aa85b6a | feasible / 0.1480 | corridor_signature |
| P05-B002-C1 | main / 1 | P05 / 28 | 0,12,21 / - | P05-B002 | 67b707910a24b9acd2b7ab2ee5b33a9c7fb3e3e31aff95e77bc49f980c9a9ef3 | feasible / 1.4522 | accepted_prefix |
| P06-B001-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B001 | e3a7980ef2c84b3fa0598eaf93f783e1788c499ad024eab26ba75c870e755ffc | feasible / 1.0981 | accepted_prefix; validation_record_missing |
| P06-B002-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B002 | 533f3e32fd01e1d3dc11264a24379c91d070480e59f3d6d2e22890d640d80c82 | feasible / 1.0532 | accepted_prefix; validation_record_missing |
| P06-B003-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B003 | e932f6ac9a44d64362d3a812b5eee93aa689ff2f473148fc60ba37bc33f6c9db | feasible / 1.5239 | accepted_prefix; validation_record_missing |
| P06-B004-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B004 | d3ac0b369d9335f9f09342bc82aa58073077a919ba0d49ada6cad44fe20fe565 | feasible / 0.5856 | accepted_prefix; validation_record_missing |
| P06-B005-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B005 | 2a070fcedaff5205d3c935b6a39d7efe7d4784dc8a6b7586a4fb6cb3e80a4346 | feasible / 1.1932 | accepted_prefix; validation_record_missing |
| P06-B006-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B006 | 7aad96c38e1e29df487404a38ec7fbbb8ad5ff1d1237055814cd4db5a6f7233a | feasible / 0.1786 | accepted_prefix; validation_record_missing |
| P06-B007-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B007 | d8eacf089263c672e53025884f4e97703262dfde111a85465a0e7af92a1300f7 | feasible / 0.1760 | accepted_prefix; validation_record_missing |
| P06-B008-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B008 | cffb4727e6524bfadd7f04946d7aa335192a07ed459f09e1e0ad0d83b47568ed | feasible / 0.1621 | accepted_prefix |
| P06-B009-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B009 | 13e4138b422b3a99a1586b8743f63680bd10b3472cf923326dc7725c4ef2e63c | feasible / 0.1770 | accepted_prefix |
| P06-B011-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B011 | 1012a87c0580a743cad9810fbe6e7032f46df0e51725538ffec8be8e6953791e | feasible / 0.1955 | accepted_prefix |
| P06-B012-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B012 | e1bb20a8591c1b7c5f9c10a7f6c905ad90d43073d668223571c20d01bc6bb14c | feasible / 0.1784 | accepted_prefix |
| P06-B013-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B013 | 81ac51fc84e3594c95d3042fa1c175feb1638ce3406810955370173ac7b2509b | feasible / 0.1779 | accepted_prefix |
| P06-B014-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B014 | 52beaada21f9d55b3a444e74f1b073105584cc634a88dff64250d059b3630c5b | feasible / 0.1733 | accepted_prefix |
| P06-B015-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B015 | 5e3fff5d6c0cc95b80f2f5d2b443eb533b51e1d571f653d2f2a6e035835983c6 | feasible / 0.1780 | accepted_prefix |
| P06-B016-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B016 | cab744a7b3b331d6efb6366f51921c9f7ba2bcc08c2776a77735099fd29dd109 | feasible / 0.1646 | accepted_prefix |
| P06-B017-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B017 | 5c7b001b4ce75671ee2641386778fe19ccf9225dfe99b60e1709c449f014d0fb | feasible / 0.1676 | accepted_prefix |
| P06-B018-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B018 | c2f66fbc6c2bc59a8693b61fd35b683c276027d08ace59191b8eec64c48b1a57 | feasible / 0.1684 | accepted_prefix |
| P06-B019-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B019 | 47fcc538fdab5f9b8ea22db26586cdfd1a216d77fac8c26201fd03d352bc51ad | feasible / 0.1643 | accepted_prefix |
| P06-B020-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B020 | ab9911fe2fdeaaf5df1bb203ed75741776c4d354420a09c9bb505a118e986ac4 | feasible / 0.1658 | accepted_prefix |
| P06-B021-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B021 | 7243d23cc9ec6f0535e39c203d76ef4d83fb64497b779d75887b20a50f5a0f63 | feasible / 0.1504 | accepted_prefix |
| P06-B022-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B022 | b7d230ba30544379f5bca0a2c17e435335dad0058293d6ebf4ca95fd07101c79 | feasible / 0.1709 | accepted_prefix |
| P06-B024-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B024 | bf0b1200f82fae362a8a3536e82ba09ac112d7d7418fc595d856c11844858d0e | feasible / 0.1647 | accepted_prefix |
| P06-B025-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B025 | e99261bec6dd387964bbd986c2b0105b30a235767a52552ec6403aa47f407cf9 | feasible / 0.1666 | accepted_prefix |
| P06-B026-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B026 | 71a6593a370b050d0164d2e0e2f4fe26bb39784362e9b16bcba7065c277d010c | feasible / 0.1595 | accepted_prefix |
| P06-B027-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B027 | 2d7d5e63eb27698a859adff2274d39e2613926eae8094786437ba50d06b7971e | feasible / 0.1548 | accepted_prefix |
| P06-B028-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B028 | fc7ae1bdf3b16ccf305a3d85395152812f0e0bdb06f98dcaa97999f6d6829717 | feasible / 0.1601 | accepted_prefix |
| P06-B029-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B029 | fd2bed6313fb74729e16b1d04f423901d4be4e68f7ce94a7c5432a43dd5fddd0 | feasible / 0.1612 | accepted_prefix |
| P06-B031-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B031 | f59ca0827da38fcdf8a1da78324b00cf8c0f03169f4e30ec34bef672dceb41db | feasible / 0.1642 | accepted_prefix |
| P06-B032-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B032 | 7f6d9dd0cd84da5d651323db8b1a2d43484a06ba057ba142821a076d8b3d32a2 | feasible / 0.1730 | accepted_prefix |
| P06-B033-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B033 | af84e6de8f08a273d946a1bba8c44d033e670ad9149a1f810030b8d625bf654c | feasible / 0.1813 | corridor_signature |
| P06-B035-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B035 | 97ae7623e05d8eae93876f473dcd99856da73bf7bc7713da383a9bb70743e84a | feasible / 0.1678 | accepted_prefix |
| P06-B036-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B036 | 2203c0ded9e368ba6008ea0ff116fde907cc6d2eb637e486823359a22cde1e75 | feasible / 0.1673 | accepted_prefix |
| P06-B038-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B038 | 9de7cc5b8e95c3bd4c1ae628b119007d3c6f7636300307080666fea78cbdde4f | feasible / 0.1737 | accepted_prefix |
| P06-B039-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B039 | d1714bcc10f5cf4bfdc80aaac5b9653e526d1a0d750a655bd794b61007c2fe2b | feasible / 0.1975 | accepted_prefix |
| P06-B040-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B040 | ac7123610f3bc9c9381615c9adfedd6de10f772005bf0cf990b08d2f99ee5ee0 | feasible / 1.2304 | accepted_prefix |
| P06-B041-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B041 | e58abe018631b98b50610955dd6f17cb5ff9a593e0257dceca6eb190a2d073ed | feasible / 0.1575 | accepted_prefix |
| P06-B042-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B042 | 158fc37a61615759b54c2db3ad0bc41b255d6427633dafdb851fe79cdc70c877 | feasible / 0.1586 | accepted_prefix |
| P06-B043-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B043 | 5b7929f4ebd56a018f4a514b43cc726dc5541617d38ee06e6c5aa89daeb90638 | feasible / 0.1605 | accepted_prefix |
| P06-B044-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B044 | cb76fabb2881a057ffa33b13603fb01aef7bc9dd0f98e3bff721cb5e7a07cbb9 | feasible / 0.1668 | accepted_prefix |
| P06-B045-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B045 | cf49bf871605c0e7e10f4c6faf77e3c5fb2f14eb9ca120d7f33c73620e7b4e4c | feasible / 0.1675 | accepted_prefix |
| P06-B047-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B047 | cdf61f4fe014c267970a0ae7240492a2d5d745cfafd5650999d93056b3287cb5 | feasible / 0.1471 | accepted_prefix |
| P06-B048-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B048 | 4a4ea9f4d65452c9844ecb8a2243281807bd00e39659e4ca83ea21c0baeb661c | feasible / 0.1596 | accepted_prefix |
| P06-B049-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B049 | 5c988e60a2002877e6946ec28f8c8fc08cb199e4f4bd52c81c3f2392c249aa19 | feasible / 0.1733 | accepted_prefix |
| P06-B050-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B050 | 7d46f6e12405ebbeed6b2a4a0cb510cda68c9f1e62dfd508b7e0d2c53e9bdcf7 | feasible / 0.1593 | accepted_prefix |
| P06-B051-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B051 | cc92a64ffb96254780dc9fb9503f8ea1444d93da63979a83a056017f89fce292 | feasible / 0.1533 | accepted_prefix |
| P06-B053-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B053 | 80f61f94bea83ba3d5e33a1d037b63c78599264290c30c5da189de14f787bd96 | feasible / 1.2514 | accepted_prefix |
| P06-B054-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B054 | 4dabec065aa2fae17432074879eb2d056f674ca643f2dcb55010c730f3d6895d | feasible / 0.1514 | accepted_prefix |
| P06-B055-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B055 | 086951a6d146b5b067fc42cf711515b881b24b2f5ef29805949523ee2dfbe7d4 | feasible / 0.2322 | accepted_prefix |
| P06-B056-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B056 | d517f672c8cd5fe48416d4a9172a2cb3c73e3bec4db6374ac72df773f685fcc4 | feasible / 0.1606 | accepted_prefix |
| P06-B057-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B057 | 2023a4e20e5dc9ca381c13b859cde6b80edb762d2f30286c433489158e6d7814 | feasible / 0.1601 | accepted_prefix |
| P06-B058-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B058 | 86beae074f7cd7ab68cd3e97009710565c31e1731f285d15a651dddcfbfa823e | feasible / 0.1535 | accepted_prefix |
| P06-B059-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B059 | f3eab07658940ae9f9d45892b2ded6f82ce03f0ebbf80c8790cd22eac719ab59 | feasible / 0.1600 | accepted_prefix |
| P06-B060-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B060 | 4ac4e7ac002f5a179a570a545b0515439ee547ba296336b22809ed6751a646a1 | feasible / 0.1585 | accepted_prefix |
| P06-B061-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B061 | 2e811b02066b786773efdf1dfa4434ffc70bf24d51f912b629c1d647b5f91651 | feasible / 0.1598 | accepted_prefix |
| P06-B062-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B062 | a5e0422cff0355264bde1ce53174cb7b621b5c6be4828b58d56b54f6969a426d | feasible / 0.1745 | accepted_prefix |
| P06-B063-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B063 | 064a0fb9cc6d705d3974b2d7363ae537143bca44cc105711df775819e4ef9c1e | feasible / 0.1625 | accepted_prefix |
| P06-B064-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B064 | 45c81db735065745fbf2609cdea8565d1eebf4454af01047330039a775c53d3f | feasible / 0.1554 | accepted_prefix |
| P06-B065-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B065 | e77455d58652d50479d854480276caf5651e219e72c9d18bc2a07d442953cd7c | feasible / 0.1589 | accepted_prefix |
| P06-B066-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B066 | 90757400d136b31e303a540f861c89c2361ae4ed64da61b7cc7ed563503f419e | feasible / 0.1671 | accepted_prefix |
| P06-B067-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B067 | 8c701d54973660e0aed28a1fa903669599cc7434512f6ab2b206a5488a491070 | feasible / 0.1679 | accepted_prefix |
| P06-B068-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B068 | 76ceb8124ad6a8ca266943efa23d71c362e89a3870eb740002fb877d0a07edbc | feasible / 0.1552 | accepted_prefix |
| P06-B069-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B069 | 6ed233999b8a5f7d3d3c409946ff9b08480eca180513af63ce8a423729f820a4 | feasible / 0.1551 | accepted_prefix |
| P06-B070-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B070 | 5ea57732f677c4126ea3ed602d385bc5a730ad0985d0f4e0de28c45e67b80ace | feasible / 0.1579 | accepted_prefix |
| P06-B071-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B071 | ac59b565d5384e1194ea01ce13a619c21336fc275ddd9228e267aa65334403af | feasible / 0.1612 | accepted_prefix |
| P06-B072-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B072 | 665d1dcc25e04e623b6803fcac5ad93805f7c06cf62a2b2d924f9139d3c8b589 | feasible / 0.2109 | accepted_prefix |
| P06-B073-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B073 | 52ae7146d677d98ef0d8d1db007985b3918db37a5aa45503f7a864685d324486 | feasible / 0.1597 | accepted_prefix |
| P06-B074-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B074 | f94c715d157b091b0d3a90ef466fe0ce669f90f710631f954263cfb68b727d6e | feasible / 0.1651 | accepted_prefix |
| P06-B075-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B075 | d21477aadc42a740c989b5dee591474478a10a6d3d3f9dddef4d3ff4cda39d26 | feasible / 0.1627 | accepted_prefix |
| P06-B076-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B076 | 4cb3b23b20b541bcfb0acb098bdb5bb4a203623e418b1a22dbb39f62cbbc0127 | feasible / 0.1580 | accepted_prefix |
| P06-B077-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B077 | 702cf428780c28d11d1dadf51c0c97d93c06d49180a4176f0c9603734a8b33d8 | feasible / 0.1800 | accepted_prefix |
| P06-B078-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B078 | a202f62b2601dfd0e6ab9d7abf275f3c008736a9e3647c709aca005cd3a2cb0c | feasible / 0.1669 | accepted_prefix |
| P06-B079-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B079 | c02e504e95055c0eec7e183d9ad0ba944016d5dd805eee7d88b091c6059cd92f | feasible / 0.1499 | accepted_prefix |
| P06-B080-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B080 | 73ed47aaff3e2dc9e5850f0e0e364d8c926690db9cdc3e6668c6d899566e43f4 | feasible / 0.1659 | accepted_prefix |
| P06-B081-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B081 | 7ba616a9ffa1f23d12b002592f33642d7ead17f9d6c61203af00058cb1ab909d | feasible / 0.1537 | accepted_prefix |
| P06-B082-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B082 | e0bd7ec6f98bd10eec8fbef4bf22f0cff94d0ed0261bf185560b0e34298c5013 | feasible / 0.1782 | accepted_prefix |
| P06-B083-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B083 | 456163285572407d10423a50a5bc465278856ab1e2ec62c6c95bc783702f898d | feasible / 0.2063 | accepted_prefix |
| P06-B084-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B084 | a5e925e06887c431ce08c16a3393b17e809ef940ec8e39561e33c5845680c35a | feasible / 0.1592 | accepted_prefix |
| P06-B086-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B086 | 143d19dd5ea8033b8febc77171a7771d6297785fa6d6b1b678a1f19fc0d804c7 | feasible / 0.1796 | accepted_prefix |
| P06-B087-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B087 | 3f7ae49543f854f9c07f2a6130fc251e8afc6d3f8bd37f604fa40c5b0fecec41 | feasible / 0.1738 | corridor_signature |
| P06-B088-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B088 | b5b4e6c0769a3e0bc3fbd7aab77224a23f6ebd7cec784415fb84bd4655a9ec9b | feasible / 0.1821 | corridor_signature |
| P06-B090-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B090 | e72b1f961899364540059e164e177d0eb4be59549ad461bd102a5355ea4fa699 | feasible / 0.1657 | corridor_signature |
| P06-B092-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B092 | 00e1365583e223276eb9d0a80628f067ba6c4cea3bcf8aae27db753574a946bf | feasible / 0.1891 | corridor_signature |
| P06-B094-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B094 | 43abad5c7ea5ca8bd9d92beb669e7baf04d63c8ad6e2922593881c2b28111f2e | feasible / 0.1916 | corridor_signature |
| P06-B096-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B096 | 1eae8c59905a9031293bb772e460669ac3974b3ac625899951f880bdc67256ce | feasible / 0.1694 | corridor_signature |
| P06-B098-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B098 | 028365c289a27533639fe00a3d3ef298c2c3ebab765eb9df63b4a5adf4fdbda9 | feasible / 0.1840 | corridor_signature |
| P06-B100-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B100 | dc2562268e2d33d946028a56618f07c4570e1a2857560d9e9686957903ef7be5 | feasible / 0.1577 | accepted_prefix |
| P06-B102-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B102 | cf5d683981fc1162bdd85f16646c2fd040ed349487bdead832f4b904d898cbe9 | feasible / 0.1871 | accepted_prefix |
| P06-B104-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B104 | 033058eb651958a1e9c43020f2e8dc8aaea4f91b56e05b19b665b199f9fe158f | feasible / 0.1899 | accepted_prefix |
| P06-B105-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B105 | c82977c45bfb37a0b609e6d9e2d52f794aa677ae3e30dbb2d1be75b2cb6a5daf | feasible / 0.1766 | accepted_prefix |
| P06-B106-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B106 | ca7eacadc976a9e9c6bcb5e172800102c807b30a1a6e2f9e53414bf9c2e42779 | feasible / 0.1814 | corridor_signature |
| P06-B108-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B108 | be2f55fc96b925087730ffb2d87554c3d5888e116e5e7727a287adf0c432cee4 | feasible / 0.1608 | accepted_prefix; validation_record_missing |
| P06-B109-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B109 | c0a3d2e66827778a4ead11dbac491aa0d01cc2e9865964c9695c75dbc9a086d1 | feasible / 0.1812 | accepted_prefix; validation_record_missing |
| P06-B110-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B110 | 976af9124a19eaee88893d050c07f2f6661e87463f7fc0c75a33fb0e407664f7 | feasible / 0.1879 | accepted_prefix; validation_record_missing |
| P06-B111-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B111 | 80ca94468a182c90e9bac445fb07a9d946a269f618a2d5882b85ad412399c61f | feasible / 0.1489 | accepted_prefix; validation_record_missing |
| P06-B112-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B112 | e70be362ef8c0ffc6b099176b0ae882003d7ce51a52e68353317ff61f8ffb499 | feasible / 0.1702 | accepted_prefix; validation_record_missing |
| P06-B113-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B113 | 079e468747ec695ca4791e5df06f170c47a1e7feba8e97f62bd73ebd80546f5a | feasible / 0.1732 | accepted_prefix; validation_record_missing |
| P06-B114-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B114 | a44a8f5e99b10a898a0d8726d8201278ea0139e3d7c1945ab260ef159baf83fe | feasible / 0.1738 | corridor_signature |
| P06-B115-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B115 | 064d324e5a19a42e008400b651910fa9a89f6b33951c42227bdbef8f82d43a66 | feasible / 0.1744 | corridor_signature |
| P06-B117-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B117 | 99eda090cf22a3787183071aae97cf4f48bf86fe701bdf7b88e53776f79c0802 | feasible / 0.1833 | corridor_signature |
| P06-B118-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B118 | 6c236b43fc5787c2e522c1bbd4d94bcbb74703ba870dc3544a59ea7a8ddba131 | feasible / 0.1865 | corridor_signature |
| P06-B120-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B120 | 0f39562ebf2e06ecba9633588c89670956c8ae56a679a87954a817a63c23db8a | feasible / 0.1800 | corridor_signature |
| P06-B122-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B122 | 949e7e7064958cb88ee494a006448c1b864faf63eae815fddc32491a5d6ff02d | feasible / 0.1595 | accepted_prefix |
| P06-B123-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B123 | 51a0daaa8c0ecf042006f91c4d7cb1124c19a55bbd9b412426ad37da87ce0055 | feasible / 0.1703 | accepted_prefix |
| P06-B125-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B125 | 96f6d4952343a659c24796fb1e44aee8967c9ceb2c1ba5bf621dd32129bf20f2 | feasible / 0.1976 | accepted_prefix |
| P06-B127-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B127 | 55ec5a8b52b1009f47d450c079beff97d1122cc5cadfa5cf18d08e1ce05ec73e | feasible / 0.1767 | corridor_signature |
| P06-B128-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B128 | 73b1d7d6137e23de9b87b15d34490485be5d88cd4bab6b822e80f24cbdc9918a | feasible / 0.1825 | corridor_signature |
| P06-B130-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B130 | 1d630c7a8694a0e0376cb7941d96e4b3e759539f98a4794f191cd5f4b1e3ce1b | feasible / 0.1571 | corridor_signature |
| P06-B132-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B132 | 861316c42177fd86c682dc4713b79cf77046a5e66af2f6879b88ecc696d11802 | feasible / 0.1745 | corridor_signature |
| P06-B133-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B133 | 6ae7a298ce7647cfb36a085be08a90e1028f761fed587e04233527037a4d1f65 | feasible / 0.1688 | accepted_prefix |
| P06-B135-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B135 | 0d96d89284b500726b0f450dd50732f45d7d174891e167aa59c64bb6fe2ccfd7 | feasible / 0.1814 | accepted_prefix |
| P06-B138-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B138 | 54f3ac2b33488d0ca61bfcec0b083c13f040261280bbe6a28a1475160763887e | feasible / 0.1815 | corridor_signature |
| P06-B140-C1 | main / 1 | P06 / 28 | 0,16 / 12..15 | P06-B140 | 731e7e63d5f5bbb71a53258ac0789d58f8a0864da3c3c01937051fe688102048 | feasible / 0.1722 | corridor_signature |
| P06-B142-C1 | main / 2 | P06 / 28 | 0,16 / 12..15 | P06-B142 | b94060045b921b657850619724c7a85668d202a4f17a9fbe21f7f078d2c59133 | feasible / 0.1578 | accepted_prefix |
| P06-B143-C1 | main / 2 | P06 / 28 | 0,16 / 12..15 | P06-B143 | 19ea5a43ed4f8168ff05a3e24ce1fd6ef0dc94d5c461dd31f26db45ba49f54c5 | feasible / 0.1783 | accepted_prefix |
| P06-B144-C1 | main / 2 | P06 / 28 | 0,16 / 12..15 | P06-B144 | 3223ed23396d81247ad5bbfb5d4e8ec21b66120396db04f5e85243a5626c8d4c | feasible / 0.1804 | accepted_prefix |
| P06-B145-C1 | main / 2 | P06 / 28 | 0,16 / 12..15 | P06-B145 | c1b9f7776e36e4135df5d3ab8d4f18ba7cf52d3dabecde45eedc7ffa61194b34 | feasible / 0.1749 | corridor_signature |
| P06-B146-C1 | main / 2 | P06 / 28 | 0,16 / 12..15 | P06-B146 | 2df3630b40f91caf2bdac944e7079ad1c23faf7a96f558af13c933fb4a18103a | feasible / 0.1589 | accepted_prefix |
| P06-B148-C1 | main / 2 | P06 / 28 | 0,16 / 12..15 | P06-B148 | 3cf0bd9e21946c4ba8bb46f8138e46093ebbec7d4d701418f93ecd2aa1036dc9 | feasible / 0.1672 | accepted_prefix |
| P06-B149-C1 | main / 2 | P06 / 28 | 0,16 / 12..15 | P06-B149 | cea0b265fffb01af727cf26dabb9d1d2724d2481060a3058312cc78c94817e9b | feasible / 0.1891 | accepted_prefix |
| P06-B151-C1 | main / 2 | P06 / 28 | 0,16 / 12..15 | P06-B151 | 85a3ec8614fee31921e3f3c8ee9dc9f277b2a1e4545b51373bbc7900d68a23ec | feasible / 0.1663 | accepted_prefix |
| P07-B001-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B001 | e33bdabc428d9938052b213b8ed2c3eb61855ff5a0144c817cd161148b8d4fdf | feasible / 0.1443 | corridor_signature |
| P07-B002-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B002 | 926aa5bb85c1d562a45e1bff718fff6e7ca84aba90e2945927f818c399d736a5 | feasible / 0.1479 | corridor_signature |
| P07-B003-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B003 | 4c74d54c6cfa207918752702a87f24cc8d9a0fffef7c4fb282a87bcca89e4049 | feasible / 0.1441 | corridor_signature |
| P07-B005-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B005 | 9e27f31097decbbc6eef9386638f4e0cb4277d87589de33bcb6f626bebb5eac2 | feasible / 0.1501 | corridor_signature |
| P07-B006-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B006 | cb840fcdaf3fa78cb159e5f6d0fca3864b1388009d15733cce890a8f7773373a | feasible / 0.2118 | corridor_signature |
| P07-B008-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B008 | 7d959da357f76e60910da908de3b2e93dfe67aae278ddc0eae97e875923efb1f | feasible / 0.1714 | corridor_signature |
| P07-B009-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B009 | d868c6eea7b8794f1fc877b86ef47d4ff847b1ce508ee9d3938c4721e3f710c9 | feasible / 0.2701 | corridor_signature |
| P07-B011-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B011 | cfe4e4a63db5ff6611872d0ae74f855201915647d6c34ea682031c832e3db9bb | feasible / 0.1666 | corridor_signature |
| P07-B012-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B012 | b33ff62cb195e9c3e04422858e6bb8b26458ec04b030d0b1ee1be5dc7e5d2f72 | feasible / 0.1707 | corridor_signature |
| P07-B014-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B014 | 71c2060a170016e659c4f00fa983ef210cbc6fae7a27a1bcd7070d0a0642fa55 | feasible / 0.1674 | corridor_signature |
| P07-B016-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B016 | b027eeac7cef819ecd9ae0cfff8eb70bcfe529afc6315de89f21a89add62fe0d | feasible / 0.1743 | corridor_signature |
| P07-B017-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B017 | e3505f12ae4145a7e2a445533e01151018db9ac8cbafcf70c728bc811fff0d8e | feasible / 0.1711 | corridor_signature |
| P07-B019-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B019 | d021e07c073423b7b00275a3ed8e8eccc25cac330c66edebed14d7edc5081160 | feasible / 0.2810 | corridor_signature |
| P07-B022-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B022 | 31e5bc7b3343ebc7676c7caf258c291107e66fc2dc1c43dcfd1d2bb230cc808c | feasible / 0.2615 | corridor_signature |
| P07-B024-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B024 | 709be27d7d6284587b82253f95fd19c6404c18440f7f5f4446b7b6cda272a0ea | feasible / 0.1641 | corridor_signature |
| P07-B026-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B026 | c46a77ee8018449a591ce5c625cfc552c26b250a8bb98f0cb9a75b9a0d45cfc7 | feasible / 0.2184 | corridor_signature |
| P07-B029-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B029 | de31f8bbc83a960d346b4987070c2c1435cf9cf229ee0a6ca877b865a8eaf687 | feasible / 0.2593 | corridor_signature |
| P07-B030-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B030 | 24f4736a1e94ff2cb6f35fd09ba6c38749bad349357b3f1b1abb8a0f051830e0 | feasible / 2.0311 | accepted_prefix |
| P07-B032-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B032 | 82e84de33fe441f8c6a6809eb121a05939e1a6de01ec82c4262be3379ddd842f | feasible / 0.2289 | corridor_signature |
| P07-B033-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B033 | c750363fe8af5677be45d38ebef3b0424133e21c455f37be2401f3828c6ef128 | feasible / 0.1860 | corridor_signature |
| P07-B035-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B035 | ffe5f48e35f2431b3beeabf525c9a0c60bda1df1ccb0b7c25104675538ae2774 | feasible / 0.1689 | corridor_signature |
| P07-B038-C1 | main / 2 | P07 / 28 | 0,17 / 12..16 | P07-B038 | b5341b3b71ddcd37ae4d7a5cc8ef3a5b4253084b457a1b610f5b1efb49e9faf7 | feasible / 0.2489 | corridor_signature |
| P07-B039-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B039 | e97aa389edf1434eb023fa43dc45a165410dec7fffff918ed1d12ddc3425bbf4 | feasible / 0.1692 | corridor_signature |
| P07-B041-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B041 | fe61249944fbc3e2ec3026ed39c8ec97be7320a2b9f6b7e563c5873e28cc71bc | feasible / 0.1805 | corridor_signature |
| P07-B042-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B042 | 53188b220b69d2087abc03c25593d12f41f3fb6dda12a52900d3e86ab9d16e69 | feasible / 0.1697 | corridor_signature |
| P07-B044-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B044 | 5a24d99f7b06d0a47ade6117c106f0427653347d38c76643530c146e4b6abccf | feasible / 0.1627 | corridor_signature |
| P07-B045-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B045 | 213817f2ed1bb936ac0e25cc9f65dd6980c913fa87903f53baf85b53ba3394dd | feasible / 2.0726 | accepted_prefix |
| P07-B046-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B046 | 80ecfbab8d248ad89b2aa2c124ca65fc392d2eb0cb807d14f6b5a75bce41e067 | feasible / 0.1775 | corridor_signature |
| P07-B047-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B047 | ef7893f0509c5d0a57a91cd51d355f1e65de949ff5b0d927039687f76e254239 | feasible / 2.1254 | accepted_prefix |
| P07-B049-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B049 | 9c67480c778e7d2180e2583af0e317023ee07a4c624e6a2ac89ff33a0ff8f586 | feasible / 0.1649 | corridor_signature |
| P07-B050-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B050 | 47005e0cb566f623fe4e8677e0903eb23e27f3a932bae9db1093fbe35dcfe976 | feasible / 0.1680 | corridor_signature |
| P07-B051-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B051 | 32b990dfff4a021be82c228a59d5b9a4c0b674a66925fc66d79ea7acb402781a | feasible / 1.0933 | accepted_prefix |
| P07-B052-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B052 | 55ea04dfbb46300ca52fa4f3bd723f71b42ba1ed37f963dd4c06d5fc94a8d3ef | feasible / 0.1669 | corridor_signature |
| P07-B054-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B054 | 45dc4b4d6628e526c190fb0fe3e109c0c159f756e8ccdb459a7eb1d4bd8e74c5 | feasible / 0.1485 | corridor_signature |
| P07-B055-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B055 | fc6346657021c08fe265c23a9c901d7b9a49f1e34965aba2afa6048f94216480 | feasible / 0.1653 | accepted_prefix |
| P07-B056-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B056 | d54176b3e208d0aa5a03da92d8237ffed67b2e213a81baa1ecd4df3238f8ae54 | feasible / 0.1582 | corridor_signature |
| P07-B057-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B057 | aece6db94c3bdbb5a62feb9a0edfdb656cf1ce0f732a333f6838c07557bf3104 | feasible / 0.1760 | corridor_signature |
| P07-B060-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B060 | e71f8dae70ac5984d4b15a70708f58023f1d1cf7dcc09be4e0c731c2eccdd849 | feasible / 0.1539 | corridor_signature |
| P07-B061-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B061 | 08de716d84ed81fbfe39f380d52791b6d39c0d4e9116bca987545f5954ee7d62 | feasible / 0.1591 | accepted_prefix |
| P07-B062-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B062 | bc28ba4fc3631b157ff12749c80bbcd69f8d4dc6f224c19a8c7ed2cbef083fa3 | feasible / 0.2275 | corridor_signature |
| P07-B064-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B064 | b157cecef84e34fedf7b92a8589da426440ca93df7cdc60f402a4115125be552 | feasible / 0.1588 | corridor_signature |
| P07-B065-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B065 | 5cf703e974cb6fc0e2e9eb29bda7042957ccb540a88ad3f6e82525678b13f4a4 | feasible / 0.1544 | corridor_signature |
| P07-B066-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B066 | a81e76bc415727c04441a20af907dc1c0a22a77b6db1f47d5a98b5594724b5cc | feasible / 0.1694 | corridor_signature |
| P07-B068-C1 | main / 3 | P07 / 28 | 0,17 / 12..16 | P07-B068 | 78f02244d1c311f7bfa644ca47e88caf9dbbc4d82856380a5a5a27417524cf16 | feasible / 0.1690 | corridor_signature |
| P01-S01-C1 | spatial / 1 | P01 / 21 | 0,12 / - | P01-S01 | e838b866eae6d187e632862e26e0915d0a956a05dde603de2747fae7db74aaca | not individually retained / not retained | evaluation feasible; individual return not retained |
| P01-S02-C1 | spatial / 1 | P01 / 22 | not individually retained / not individually retained | P01-S02 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P01-S03-C1 | spatial / 1 | P01 / 23 | not individually retained / not individually retained | P01-S03 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P01-S03-C2 | spatial / 1 | P01 / 23 | not individually retained / not individually retained | P01-S03 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P01-S03-C3 | spatial / 1 | P01 / 23 | not individually retained / not individually retained | P01-S03 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P01-S03-C4 | spatial / 1 | P01 / 23 | not individually retained / not individually retained | P01-S03 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P02-S01-C1 | spatial / 1 | P02 / 17 | 8 / 0..7 | P02-S01 | c341eef87b4b285e501e33ddd76cbdb09d8f7116a5ae9712aa02f329a9b7ac88 | not individually retained / not retained | evaluation feasible; individual return not retained |
| P02-S02-C1 | spatial / 1 | P02 / 29 | not individually retained / not individually retained | P02-S02 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P02-S02-C2 | spatial / 1 | P02 / 29 | not individually retained / not individually retained | P02-S02 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P02-S02-C3 | spatial / 1 | P02 / 29 | not individually retained / not individually retained | P02-S02 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P02-S02-C4 | spatial / 1 | P02 / 29 | not individually retained / not individually retained | P02-S02 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P02-S02-C5 | spatial / 1 | P02 / 29 | not individually retained / not individually retained | P02-S02 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P03-S01-C1 | spatial / 1 | P03 / 30 | 0,12,21 / - | P03-S01 | fe6f24a6f9aa8431ea1c2aa518965612fc1da6a4a17ef4cb310d18d0ffc6930a | not individually retained / not retained | evaluation feasible; individual return not retained |
| P03-S02-C1 | spatial / 1 | P03 / 42 | not individually retained / not individually retained | P03-S02 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P03-S03-C1 | spatial / 1 | P03 / 43 | not individually retained / not individually retained | P03-S03 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P03-S04-C1 | spatial / 1 | P03 / 44 | not individually retained / not individually retained | P03-S04 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P04-S01-C1 | spatial / 1 | P04 / 30 | 0,12,21 / - | P04-S01 | a85ab278de3f358fe23067c05a877fd3762c09ad666059e328625a0a5cc44bbe | not individually retained / not retained | evaluation feasible; individual return not retained |
| P04-S02-C1 | spatial / 1 | P04 / 42 | not individually retained / not individually retained | P04-S02 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P04-S03-C1 | spatial / 1 | P04 / 43 | not individually retained / not individually retained | P04-S03 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P04-S04-C1 | spatial / 1 | P04 / 44 | not individually retained / not individually retained | P04-S04 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P05-S01-C1 | spatial / 1 | P05 / 30 | 0,12,21 / - | P05-S01 | 00d786ff365b0a1db93301dec7eb007bb2e2803e5e34e7926e4233d0beab33d7 | not individually retained / not retained | evaluation feasible; individual return not retained |
| P05-S02-C1 | spatial / 1 | P05 / 42 | not individually retained / not individually retained | P05-S02 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P05-S03-C1 | spatial / 1 | P05 / 43 | not individually retained / not individually retained | P05-S03 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P05-S04-C1 | spatial / 1 | P05 / 44 | not individually retained / not individually retained | P05-S04 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P06-S01-C1 | spatial / 1 | P06 / 46 | not individually retained / not individually retained | P06-S01 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P06-S01-C2 | spatial / 1 | P06 / 46 | not individually retained / not individually retained | P06-S01 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P06-S01-C3 | spatial / 1 | P06 / 46 | not individually retained / not individually retained | P06-S01 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P06-S01-C4 | spatial / 1 | P06 / 46 | not individually retained / not individually retained | P06-S01 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P06-S01-C5 | spatial / 1 | P06 / 46 | not individually retained / not individually retained | P06-S01 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P06-S01-C6 | spatial / 1 | P06 / 46 | not individually retained / not individually retained | P06-S01 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P07-S01-C1 | spatial / 2 | P07 / 47 | not individually retained / not individually retained | P07-S01 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P07-S01-C2 | spatial / 2 | P07 / 47 | not individually retained / not individually retained | P07-S01 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P07-S01-C3 | spatial / 2 | P07 / 47 | not individually retained / not individually retained | P07-S01 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P07-S01-C4 | spatial / 2 | P07 / 47 | not individually retained / not individually retained | P07-S01 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P07-S01-C5 | spatial / 2 | P07 / 47 | not individually retained / not individually retained | P07-S01 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
| P07-S01-C6 | spatial / 2 | P07 / 47 | not individually retained / not individually retained | P07-S01 | not retained | not individually retained / not retained | evaluation feasible; individual return not retained |
