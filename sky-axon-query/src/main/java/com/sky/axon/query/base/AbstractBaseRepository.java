/*
 * The MIT License (MIT)
 * Copyright © 2020 <sky>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the “Software”), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sky.axon.query.base;

import com.sky.axon.query.base.annotation.QueryField;
import com.sky.axon.query.base.util.ReflectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 基本CRUD操作实现类(MongoDB)
 *
 * @author
 */
public abstract class AbstractBaseRepository<T> implements BaseRepository<T> {

    @Autowired
    @Qualifier("mongoTemplate")
    protected MongoTemplate mongoTemplate;

    /**
     * 保存一个对象到mongodb
     *
     * @param bean
     * @return
     */
    @Override
    public T save(T bean) {
        mongoTemplate.save(bean);
        return bean;
    }

    /**
     * 根据id删除对象
     *
     * @param t
     */
    @Override
    public void deleteById(T t) {
        mongoTemplate.remove(t);
    }


    /**
     * 根据对象的属性删除
     *
     * @param t
     */
    @Override
    public void deleteByCondition(T t) {
        Query query = buildBaseQuery(t);
        mongoTemplate.remove(query, getEntityClass());
    }

    /**
     * 根据id进行更新
     *
     * @param id
     * @param t
     */
    @Override
    public void updateById(String id, T t) {
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(id));
        Update update = buildBaseUpdate(t);
        update(query, update);
    }

    /**
     * 根据对象的属性查询
     *
     * @param t
     * @return
     */
    @Override
    public List<T> findByCondition(T t) {
        Query query = buildBaseQuery(t);
        return mongoTemplate.find(query, getEntityClass());
    }

    /**
     * 通过条件查询实体(集合)
     *
     * @param query
     * @return
     */
    @Override
    public List<T> find(Query query) {
        return mongoTemplate.find(query, this.getEntityClass());
    }

    /**
     * 通过一定的条件查询一个实体
     *
     * @param query
     * @return
     */
    @Override
    public T findOne(Query query) {
        return mongoTemplate.findOne(query, this.getEntityClass());
    }

    /**
     * 通过条件查询更新数据
     *
     * @param query
     * @param update
     */
    @Override
    public void update(Query query, Update update) {
        mongoTemplate.updateMulti(query, update, this.getEntityClass());
    }

    /**
     * 通过ID获取记录
     *
     * @param id
     * @return
     */
    @Override
    public T findById(String id) {
        return mongoTemplate.findById(id, this.getEntityClass());
    }

    /**
     * 通过ID获取记录,并且指定了集合名(表的意思)
     *
     * @param id
     * @param collectionName
     * @return
     */
    @Override
    public T findById(String id, String collectionName) {
        return mongoTemplate.findById(id, this.getEntityClass(), collectionName);
    }

    /**
     * 通过条件查询,查询分页结果
     *
     * @param page
     * @param query
     * @return
     */
    @Override
    public Page<T> findPage(Page<T> page, Query query) {
        //如果没有条件 则所有全部
        query = query == null ? new Query(Criteria.where("_id").exists(true)) : query;
        long count = this.count(query);
        // 总数
        page.setTotalCount((int) count);
        int currentPage = page.getCurrentPage();
        int pageSize = page.getPageSize();
        query.skip((currentPage - 1) * pageSize).limit(pageSize);
        List<T> rows = this.find(query);
        page.build(rows);
        return page;
    }

    /**
     * 求数据总和
     *
     * @param query
     * @return
     */
    @Override
    public long count(Query query) {
        return mongoTemplate.count(query, this.getEntityClass());
    }

    /**
     * 根据vo构建查询条件Query
     *
     * @param t
     * @return
     */
    private Query buildBaseQuery(T t) {
        Query query = new Query();

        Field[] fields = t.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(t);
                if (value != null) {
                    QueryField queryField = field.getAnnotation(QueryField.class);
                    if (queryField != null) {
                        query.addCriteria(queryField.type().buildCriteria(queryField, field, value));
                    }
                }
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return query;
    }

    /**
     * 根据vo构建更新条件Query
     *
     * @param t
     * @return
     */
    private Update buildBaseUpdate(T t) {
        Update update = new Update();

        Field[] fields = t.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(t);
                if (value != null) {
                    update.set(field.getName(), value);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return update;
    }

    /**
     * 获取需要操作的实体类class
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    protected Class<T> getEntityClass() {
        // TODO 这种方式也可以 return ((Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0]);
        return ReflectionUtils.getSuperClassGenricType(getClass());
    }

    /**
     * 获取MongoDB模板操作
     *
     * @return
     */
    @Override
    public MongoTemplate getMongoTemplate() {
        return mongoTemplate;
    }
}
