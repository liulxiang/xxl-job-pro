$(function() {

	// init date tables
	var apiclientTable = $("#apiclient_list").dataTable({
		"deferRender": true,
		"processing" : true,
	    "serverSide": true,
		"ajax": {
			url: base_url + "/apiclient/pageList",
			type:"post",
	        data : function ( d ) {
	        	var obj = {};
                obj.appKey = $('#appKey').val();
	        	obj.start = d.start;
	        	obj.length = d.length;
                return obj;
            }
	    },
	    "searching": false,
	    "ordering": false,
	    "columns": [
	                {
	                	"data": 'id',
						"visible" : true,
						"width":'5%'
					},
	                {
	                	"data": 'appKey',
						"visible" : true,
						"width":'18%'
					},
	                {
	                	"data": 'appSecret',
						"visible" : true,
						"width":'15%'
					},
					{
						"data": 'username',
						"visible" : true,
						"width":'12%'
					},
	                {
	                	"data": 'callbackUrl',
						"width":'20%',
						"render": function ( data, type, row ) {
							return data || '';
						}
	                },
					{
						"data": 'status',
						"visible" : true,
						"width":'10%',
						"render": function ( data, type, row ) {
							if (data == 1) {
								return I18n.apiclient_status_enabled
							} else {
								return I18n.apiclient_status_disabled
							}
						}
					},
					{
						"data": 'remark',
						"visible" : true,
						"width":'10%'
					},
	                {
						"data": I18n.system_opt ,
						"width":'15%',
	                	"render": function ( data, type, row ) {
	                		return function(){
								tableData['key'+row.id] = row;
								var html = '<p id="'+ row.id +'" >'+
									'<button class="btn btn-warning btn-xs update" type="button">'+ I18n.system_opt_edit +'</button>  '+
									'<button class="btn btn-danger btn-xs delete" type="button">'+ I18n.system_opt_del +'</button>  '+
									'</p>';
	                			return html;
							};
	                	}
	                }
	            ],
		"language" : {
			"sProcessing" : I18n.dataTable_sProcessing ,
			"sLengthMenu" : I18n.dataTable_sLengthMenu ,
			"sZeroRecords" : I18n.dataTable_sZeroRecords ,
			"sInfo" : I18n.dataTable_sInfo ,
			"sInfoEmpty" : I18n.dataTable_sInfoEmpty ,
			"sInfoFiltered" : I18n.dataTable_sInfoFiltered ,
			"sInfoPostFix" : "",
			"sSearch" : I18n.dataTable_sSearch ,
			"sUrl" : "",
			"sEmptyTable" : I18n.dataTable_sEmptyTable ,
			"sLoadingRecords" : I18n.dataTable_sLoadingRecords ,
			"sInfoThousands" : ",",
			"oPaginate" : {
				"sFirst" : I18n.dataTable_sFirst ,
				"sPrevious" : I18n.dataTable_sPrevious ,
				"sNext" : I18n.dataTable_sNext ,
				"sLast" : I18n.dataTable_sLast
			},
			"oAria" : {
				"sSortAscending" : I18n.dataTable_sSortAscending ,
				"sSortDescending" : I18n.dataTable_sSortDescending
			}
		}
	});

    // table data
    var tableData = {};

	// search btn
	$('#searchBtn').on('click', function(){
        apiclientTable.fnDraw();
	});

	// delete
	$("#apiclient_list").on('click', '.delete',function() {
		var id = $(this).parent('p').attr("id");

		layer.confirm( I18n.system_ok + I18n.system_opt_del + '?', {
			icon: 3,
			title: I18n.system_tips ,
            btn: [ I18n.system_ok, I18n.system_cancel ]
		}, function(index){
			layer.close(index);

			$.ajax({
				type : 'POST',
				url : base_url + "/apiclient/remove",
				data : {
					"id" : id
				},
				dataType : "json",
				success : function(data){
					if (data.code == 200) {
                        layer.msg( I18n.system_success );
						apiclientTable.fnDraw(false);
					} else {
                        layer.msg( data.msg || I18n.system_opt_del + I18n.system_fail );
					}
				}
			});
		});
	});

	// add
	$(".add").click(function(){
		$('#addModal').modal({backdrop: false, keyboard: false}).modal('show');
	});
	$("#addModal .form").validate({
		errorElement : 'span',
        errorClass : 'help-block',
        focusInvalid : true,
        rules : {
            appKey : { required : true },
            appSecret : { required : true }
        },
        messages : {
            appKey : { required : I18n.system_please_input + I18n.apiclient_app_key },
            appSecret : { required : I18n.system_please_input + I18n.apiclient_app_secret }
        },
		highlight : function(element) {
            $(element).closest('.form-group').addClass('has-error');
        },
        success : function(label) {
            label.closest('.form-group').removeClass('has-error');
            label.remove();
        },
        errorPlacement : function(error, element) {
            element.parent('div').append(error);
        },
        submitHandler : function(form) {
			var paramData = {
				"appKey": $("#addModal .form input[name=appKey]").val(),
                "appSecret": $("#addModal .form input[name=appSecret]").val(),
                "username": $("#addModal .form select[name=username]").val(),
                "callbackUrl": $("#addModal .form input[name=callbackUrl]").val(),
                "status": $("#addModal .form input[name=status]:checked").val(),
                "remark": $("#addModal .form input[name=remark]").val()
			};

        	$.post(base_url + "/apiclient/add", paramData, function(data, status) {
    			if (data.code == "200") {
					$('#addModal').modal('hide');
                    layer.msg( I18n.system_add_suc );
                    apiclientTable.fnDraw();
    			} else {
					layer.open({
						title: I18n.system_tips ,
                        btn: [ I18n.system_ok ],
						content: (data.msg || I18n.system_add_fail),
						icon: '2'
					});
    			}
    		});
		}
	});
	$("#addModal").on('hide.bs.modal', function () {
		$("#addModal .form")[0].reset();
		$("#addModal .form .form-group").removeClass("has-error");
	});

	// update
	$("#apiclient_list").on('click', '.update',function() {
        var id = $(this).parent('p').attr("id");
        var row = tableData['key'+id];

		$("#updateModal .form input[name='id']").val( row.id );
		$("#updateModal .form input[name='appKey']").val( row.appKey );
		$("#updateModal .form input[name='appSecret']").val( '' );
		$("#updateModal .form select[name='username']").val( row.username );
		$("#updateModal .form input[name='callbackUrl']").val( row.callbackUrl || '' );
		$("#updateModal .form input[name='status'][value='"+ row.status +"']").click();
		$("#updateModal .form input[name='remark']").val( row.remark || '' );

		$('#updateModal').modal({backdrop: false, keyboard: false}).modal('show');
	});
	$("#updateModal .form").validate({
		errorElement : 'span',
        errorClass : 'help-block',
        focusInvalid : true,
		highlight : function(element) {
            $(element).closest('.form-group').addClass('has-error');
        },
        success : function(label) {
            label.closest('.form-group').removeClass('has-error');
            label.remove();
        },
        errorPlacement : function(error, element) {
            element.parent('div').append(error);
        },
        submitHandler : function(form) {
            var paramData = {
                "id": $("#updateModal .form input[name=id]").val(),
                "appKey": $("#updateModal .form input[name=appKey]").val(),
                "appSecret": $("#updateModal .form input[name=appSecret]").val(),
                "username": $("#updateModal .form select[name=username]").val(),
                "callbackUrl": $("#updateModal .form input[name=callbackUrl]").val(),
                "status": $("#updateModal .form input[name=status]:checked").val(),
                "remark": $("#updateModal .form input[name=remark]").val()
            };

            $.post(base_url + "/apiclient/update", paramData, function(data, status) {
                if (data.code == "200") {
                    $('#updateModal').modal('hide');
                    layer.msg( I18n.system_update_suc );
                    apiclientTable.fnDraw();
                } else {
                    layer.open({
                        title: I18n.system_tips ,
                        btn: [ I18n.system_ok ],
						content: (data.msg || I18n.system_update_fail),
						icon: '2'
                    });
                }
            });
		}
	});
	$("#updateModal").on('hide.bs.modal', function () {
        $("#updateModal .form")[0].reset();
        $("#updateModal .form .form-group").removeClass("has-error");
	});

});
