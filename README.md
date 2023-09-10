# rrcommunity
点滴留影项目
项目描述：点滴留影是一个便于用户记录自己打卡经历的掌上单体项目
技术栈：SpringBoot、MyBatisPlus、Redis、MySQL、Lombok、Hotool
项目用SpringBoot作为技术框架，采用Redis存储需要大量访问的数据，提高访问性能，减少数据库压力，同时解决缓存击穿，缓存穿透，缓存雪崩问题
同时采用MySQL集群，实现主从复制，读写分离，增强查询性能
使用MyBatisPlus作为持久层框架
