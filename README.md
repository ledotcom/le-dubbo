le-dubbo is dubbo used in lemall.com which is an e-commerce website.le-dubbo keeps in sync with alibaba dubbo and dangdang dubbox,obtain their new features and add new functions for lemall high concurrency scenario.

le-dubbo 是乐视电子商务网站在当当开源的dubbox-2.8.4基础上，基于自身的业务需求不断完善形成的，有根据业务需求新增的功能，也有在使用过程中发现的bug修复。

le-dubbo 承诺完全同步dubbox 与dubbo的最新代码版本功能。

#主要开发者
邓宏 乐视电子商务 denghong1@le.com http://www.cnblogs.com/dimmacro QQ:810259563

由于目前dubbox和dubbo活跃性不太好，且目前各公司对dubbo都有较大使用更新需求，欢迎大家加入le-dubbo的维护。

#升级维护说明
le-dubbo主要是为解决乐视商城在使用dubbox过程中遇到的各种需求及bug修改，使用Git的版本推荐管理方式，即采用分支开发，使用稳定后同步到主干master的方式，既可以保证分支的快速迭代开发，也可以保证主干的稳定。为方便区分，每个分支的命名格式为：ledubbo-2.8.4.x，其中x为变动版本，主要是新增一些小需求改动及修改bug，后续如果改动较大，会考虑升级第三个版本号。

---------------------  --------版本及修改说明------------------------------------
2.8.4.3	在前一版本基础上新增了调用链信息及调用耗时信息
2.8.4.2	在前一版本基础上新增了redis哨兵模式作为注册中心
2.8.4.1	在前一版本基础上新增了Kryo统一序列化封装
2.8.4	当当dubbox基础版

#当前最新开发版本
2.8.4.3
