(window["webpackJsonp"]=window["webpackJsonp"]||[]).push([["chunk-067fca09"],{"592a":function(t,e,i){},"688b":function(t,e,i){"use strict";i.r(e);var n=function(){var t=this,e=t.$createElement,i=t._self._c||e;return i("div",[i("div",{staticClass:"search"},[i("el-input",{attrs:{placeholder:"请输入内容"},model:{value:t.title,callback:function(e){t.title=e},expression:"title"}}),i("el-button",{attrs:{type:"primary"},on:{click:t.getVideoList}},[t._v("搜索")])],1),i("div",[i("el-row",{attrs:{gutter:20}},t._l(t.videoList,(function(e,n){return i("el-col",{key:n,attrs:{span:6}},[i("div",{staticStyle:{"margin-bottom":"20px"}},[i("el-card",{staticClass:"card",staticStyle:{width:"400px"},attrs:{"body-style":{padding:"20px"}}},[i("el-image",{staticClass:"image video-image",staticStyle:{width:"300px",height:"200px"},attrs:{src:e.image}}),i("div",{staticStyle:{padding:"14px"}},[i("el-link",{attrs:{type:"info"},on:{click:function(i){return t.open(e)}}},[t._v(t._s(e.title))])],1),i("div",[i("span",[t._v("发布时间: "+t._s(e.publishTime))])])],1)],1)])})),1),i("el-pagination",{attrs:{"current-page":t.page,"page-sizes":[10,50,100,300],"page-size":t.size,layout:"total, sizes, prev, pager, next, jumper",total:t.total},on:{"size-change":t.handleSizeChange,"current-change":t.handleCurrentChange}})],1)])},a=[],s=i("c24f"),l={name:"video-list",data:function(){return{visible:!1,currentDate:new Date,page:1,size:20,title:null,total:0,videoList:[],params:{title:"",current:1,size:10}}},created:function(){this.getVideoList()},methods:{open:function(t){window.open(t.url)},getVideoList:function(){var t=this,e=new FormData;e.append("current",this.page),e.append("size",this.size),null!==this.title&&e.append("title",this.title),Object(s["b"])(e).then((function(e){t.videoList=e.data.content,t.total=e.data.totalElements}))},handleSizeChange:function(t){this.size=t,document.documentElement.scrollTop=0,this.getVideoList()},handleCurrentChange:function(t){document.documentElement.scrollTop=0,this.page=t,this.getVideoList()}}},o=l,c=(i("6f90"),i("2877")),r=Object(c["a"])(o,n,a,!1,null,"5311f122",null);e["default"]=r.exports},"6f90":function(t,e,i){"use strict";i("592a")}}]);